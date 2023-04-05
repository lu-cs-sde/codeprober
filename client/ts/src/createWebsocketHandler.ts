
type HandlerFn = (data: { [key: string]: any }) => void;

interface  WebsocketHandler {
  sendRpc: (msg: any) => Promise<any>;
  on: (msgId: string, callback: HandlerFn) => void;
}

let rpcIdGenerator = 1;

const createWebsocketHandler = (
  socket: WebSocket,
  onClose: (didReceiveAtLeastOneMessage: boolean) => void,
): WebsocketHandler => {
  const pendingCallbacks: { [id: string]: HandlerFn } = {};
  const messageHandlers: { [opName: string]: HandlerFn } = {
    rpc: ({ id, ...res }) => {
      const handler = pendingCallbacks[id];
      if (handler) {
        delete pendingCallbacks[id];
        handler(res);
      } else {
        console.warn('Received RPC response for', id, ', expected one of', Object.keys(pendingCallbacks));
      }
    },
  };

  let didReceiveAtLeastOneMessage = false;
  socket.addEventListener('message', function (event) {
    didReceiveAtLeastOneMessage = true;
    // console.log('Message from server ', event.data);
    const parsed = JSON.parse(event.data);
    if (messageHandlers[parsed.type]) {
      messageHandlers[parsed.type](parsed);
    } else {
      console.log('No handler for message', parsed, ', got handlers for', Object.keys(messageHandlers));
    }
  });
  socket.addEventListener('close', () => {
    // Small timeout to reduce risk of it appearing when navigating away
    setTimeout(() => onClose(didReceiveAtLeastOneMessage), 100);
  });

  return {
    on: (id, cb) => messageHandlers[id] = cb,
    sendRpc: (msg) => new Promise(async (res, rej) => {
      const id = rpcIdGenerator++;
      socket.send(JSON.stringify({
        ...msg,
        id,
      }));

      const cleanup = () => delete pendingCallbacks[id];
      pendingCallbacks[id] = ({ error, result }) => {
        cleanup();
        if (error) {
          console.warn('RPC request failed', error);
          rej(error);
        } else {
          res(result);
        }
      };

      setTimeout(() => {
        cleanup();
        rej('Timeout');
      }, 30000);
    }),
  };
};

const createWebsocketOverHttpHandler = (
  onClose: (didReceiveAtLeastOneMessage: boolean) => void,
): WebsocketHandler => {
  const pendingCallbacks: { [id: string]: HandlerFn } = {};
  const messageHandlers: { [opName: string]: HandlerFn } = {
    rpc: ({ id, ...res }) => {
      const handler = pendingCallbacks[id];
      if (handler) {
        delete pendingCallbacks[id];
        handler(res);
      } else {
        console.warn('Received RPC response for', id, ', expected one of', Object.keys(pendingCallbacks));
      }
    },
  };

  let didReceiveAtLeastOneMessage = false;
  const defaultRetryBudget = 3;
  const session = `cpr_${(Number.MAX_SAFE_INTEGER * Math.random())|0}`

  const wsHandler: WebsocketHandler = {
    on: (id, cb) => messageHandlers[id] = cb,
    sendRpc: (msg) => new Promise(async (res, rej) => {
      const id = rpcIdGenerator++;
      const body = { ...msg, id };

      pendingCallbacks[id] = ({ error, result }) => {
        cleanup();
        if (error) {
          console.warn('RPC request failed', error);
          rej(error);
        } else {
          res(result);
        }
      };
      const cleanup = () => delete pendingCallbacks[id];

      const attemptFetch = async (remainingTries: number) => {
        const cleanupTimer = setTimeout(() => {
          cleanup();
          rej('Timeout');
        }, 30000);
        try {
          const rawFetchResult = await fetch('/wsput', { method: 'PUT', body: JSON.stringify({...body, session}) })
          if (!rawFetchResult.ok) {
            if (remainingTries > 0) {
              console.warn('wsput request failed, trying again in 1 second..');
              clearTimeout(cleanupTimer);
              setTimeout(() => attemptFetch(remainingTries - 1), 1000);
              return;
            }
          }
          const fetchResult = await rawFetchResult.json();
          didReceiveAtLeastOneMessage = true;
          if (messageHandlers[fetchResult.type]) {
            messageHandlers[fetchResult.type](fetchResult);
            cleanup();
            res(true);
          } else {
            console.log('No handler for message', fetchResult, ', got handlers for', Object.keys(messageHandlers));
            cleanup();
            rej('Bad response');
          }
        } catch (e) {
          console.warn('Error when performing ws-over-http request', e);
          cleanup();
          rej('Unknown error');
        }
      };
      attemptFetch(defaultRetryBudget);
    }),
  };
  wsHandler.sendRpc({ type: 'init' });

  let prevEtagValue = -1;
  let longPoller = async (retryBudget: number) => {
    try {
      const rawFetchResult = await fetch('/wsput', { method: 'PUT', body: JSON.stringify({
        id: -1, type: 'longpoll', etag: prevEtagValue, session
      }) });
      if (!rawFetchResult.ok) {
        throw new Error(`Fetch result: ${rawFetchResult.status}`);
      }
      const pollResult = await rawFetchResult.json();
      if (pollResult.etag) {
        const { etag } = pollResult;
        if (prevEtagValue !== etag) {
          if (prevEtagValue !== -1) {
            if (messageHandlers.refresh) {
              messageHandlers.refresh({});
            }
          }
          prevEtagValue = etag;
        }
      } else if (pollResult.type === 'push') {
        const { message } = pollResult;
        const handler = messageHandlers[message.type];
        if (handler) {
          handler(message);
        } else {
          console.warn('Got /wsput push message of unknown type', message);
        }
      }
    } catch (e) {
      console.warn('Error during longPoll', e);
      if (retryBudget > 0) {
        console.log('Retrying longpoll in 1 second');
        setTimeout(() => {
          longPoller(retryBudget - 1);
        }, 1000);
      } else {
        onClose(didReceiveAtLeastOneMessage);
      }
      return;
    }
    setTimeout(() => longPoller(defaultRetryBudget), 1);
  };
  longPoller(defaultRetryBudget);
  return wsHandler;
};
export { WebsocketHandler, createWebsocketOverHttpHandler }
export default createWebsocketHandler;

import { TopRequestReq, TopRequestRes, TunneledWsPutRequestReq, TunneledWsPutRequestRes, WsPutInitReq, WsPutInitRes, WsPutLongpollReq, WsPutLongpollRes } from './protocol';

type HandlerFn = (data: any) => void;

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
      const topReq: TopRequestReq = { type: 'rpc', id, data: msg, };
      socket.send(JSON.stringify(topReq));

      const cleanup = () => delete pendingCallbacks[id];
      pendingCallbacks[id] = (callback: TopRequestRes) => {
        cleanup();
        switch (callback.data.type) {
          case 'success': {
            res(callback.data.value);
            break;
          }
          case 'failureMsg': {
            console.warn('RPC request failed', callback.data.value);
            rej(callback.data.value);
            break;
          }
          default: {
            console.error('Unexpected RPC response');
            rej(JSON.stringify(callback));
            break;
          }
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
  const session = `cpr_${(Number.MAX_SAFE_INTEGER * Math.random())|0}`;


  const sendRpc = (msg: any, extraArgs?: { timeout: number, }): Promise<any> => new Promise(async (res, rej) => {
    const id = rpcIdGenerator++;
    // const body = { ...msg, id };
    const topReq: TopRequestReq = { type: 'rpc', id, data: msg, };
    // {...topReq, session}

    // pendingCallbacks[id] = ({ error, result }) => {
    //   cleanup();
    //   if (error) {
    //     console.warn('RPC request failed', error);
    //     rej(error);
    //   } else {
    //     res(result);
    //   }
    // };
    pendingCallbacks[id] = (callback: TopRequestRes) => {
      cleanup();
      switch (callback.data.type) {
        case 'success': {
          res(callback.data.value);
          break;
        }
        case 'failureMsg': {
          console.warn('RPC request failed', callback.data.value);
          rej(callback.data.value);
          break;
        }
        default: {
          console.error('Unexpected RPC response');
          rej(JSON.stringify(callback));
          break;
        }
      }
    }

    const cleanup = () => delete pendingCallbacks[id];

    const attemptFetch = async (remainingTries: number) => {
      const cleanupTimer = setTimeout(() => {
        cleanup();
        rej('Timeout');
      }, extraArgs?.timeout ?? 30000);
      try {
        const rawFetchResult = await fetch('/wsput', { method: 'PUT', body: JSON.stringify(topReq) })
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
  });

  const wsHandler: WebsocketHandler = {
    on: (id, cb) => messageHandlers[id] = cb,
    sendRpc: async (msg) => {
      const wrapped: TunneledWsPutRequestReq = {
        type: 'wsput:tunnel',
        session,
        request: msg,
      };
      const res: TunneledWsPutRequestRes = await sendRpc(wrapped);
      return res.response;
    },
  };

  const initReq: WsPutInitReq = ({ type: 'wsput:init', session });
  sendRpc(initReq)
    .then((init: WsPutInitRes) => {
      if (messageHandlers['init']) {
        messageHandlers['init'](init.info);
      } else {
        console.warn('Got init message, but no handler for it')
      }
    })
    .catch(err => {
      console.warn('Failed to get init message', err);
    })

  let prevEtagValue = -1;
  let longPoller = async (retryBudget: number) => {
    try {
      const longPollRequest: WsPutLongpollReq = {
        type: 'wsput:longpoll',
        session,
        etag: prevEtagValue,
      };
      const result: WsPutLongpollRes = await sendRpc(longPollRequest, { timeout: 10 * 60 * 1000 });
      if (result.data) {
        switch (result.data.type) {
          case 'etag': {
            const etag = result.data.value;
            if (prevEtagValue !== etag) {
              if (prevEtagValue !== -1) {
                if (messageHandlers.refresh) {
                  messageHandlers.refresh({});
                }
              }
              prevEtagValue = etag;
            }
            break;
          }
          case 'push': {
            const message = result.data.value;
            const handler = messageHandlers[message.type];
            if (handler) {
              handler(message);
            } else {
              console.warn('Got /wsput push message of unknown type', message);
            }
            break;
          }
          default: {
            console.warn('Unknown longpoll response type:', result.data);
            break;
          }
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

interface LocalRequestHandler {
  submit: (message: { [id: string]: any }) => Promise<{ [id: string]: any }>;
  // onRefresh: (callback: () => void) => void;
  on: (callbackType: string, callback: (data: any) => void) => void;
}

const createLocalRequestHandler = (
  handler: LocalRequestHandler,
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
  const session = `cpr_${(Number.MAX_SAFE_INTEGER * Math.random())|0}`;

  const sendRpc = (msg: any, extraArgs?: { timeout: number, }): Promise<any> => new Promise(async (res, rej) => {

    const attemptFetch = async () => {
      const cleanupTimer = setTimeout(() => {
        // cleanup();
        rej('Timeout');
      }, extraArgs?.timeout ?? 30000);
      try {
        let fetchResult;
        try {
          fetchResult = await handler.submit(msg);
        } catch (e) {
          console.warn('Failed local request handler for', msg);
          // cleanup();
          clearTimeout(cleanupTimer);
          return rej(e);
        }
        console.log('attemptFetch got', fetchResult)
        didReceiveAtLeastOneMessage = true;
        res(fetchResult);
      } catch (e) {
        console.warn('Error when performing ws-over-http request', e);
        rej('Unknown error');
      }
    };
    attemptFetch().catch(rej);
  });

  const wsHandler: WebsocketHandler = {
    on: (id, cb) => messageHandlers[id] = cb,
    sendRpc,
  };

  const initReq: WsPutInitReq = ({ type: 'wsput:init', session });
  sendRpc(initReq)
    .then((init: WsPutInitRes) => {
      console.log('got init response')
      if (messageHandlers['init']) {
        console.log('call handler w/ info..', init.info)
        messageHandlers['init'](init.info);
      } else {
        console.warn('Got init message, but no handler for it')
      }
    })
    .catch(err => {
      console.warn('Failed to get init message', err);
    })

    handler.on('refresh', (data) => {
      if (messageHandlers['refresh']) {
        messageHandlers['refresh'](data || {});
      }
    });
    handler.on('asyncUpdate', (data) => {
      if (messageHandlers['asyncUpdate']) {
        messageHandlers['asyncUpdate'](data || {});
      }
    });
  return wsHandler;
};


export { WebsocketHandler, createWebsocketOverHttpHandler, LocalRequestHandler, createLocalRequestHandler }
export default createWebsocketHandler;

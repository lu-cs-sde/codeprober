import { AsyncRequestReq, AsyncRequestRes, GetDecorationsRes, StopJobReq, StopJobRes } from '../protocol';
import ModalEnv from './ModalEnv';


type AsyncResult<T> =
  { type: 'ok', value: T }
| { type: 'timeout' }
| { type: 'unexpected_error', value: string[] }
;

const evaluateAsyncRequest = <Req, Res>(env: ModalEnv, req: Req): Promise<AsyncResult<Res>> => new Promise(async (resolve, reject) => {
  try {
    let autoKillTimer: any | null = null;
    const jobId = env.createJobId(update => {
      switch (update.value.type) {
        case 'workerTaskDone': {
          if (autoKillTimer !== null) {
            clearTimeout(autoKillTimer);
          }

          const wtd = update.value.value;
          switch (wtd.type) {
            case 'normal': {
              const res = wtd.value as AsyncRequestRes;
              if (res.response.type == 'job') {
                throw new Error(`Unexpected 'job' result in workerTaskDone`);
              }
              resolve({ type: 'ok', value: res.response.value as Res });
              break;
            }
            case 'unexpectedError': {
              resolve({ type: 'unexpected_error', value: wtd.value });
              break;
            }
          }
          break;
        }
        default: {
          // Ignore
          break;
        }
      }
    });
    const res = await env.performTypedRpc<AsyncRequestReq, AsyncRequestRes>({
      type: 'AsyncRequest',
      src: req as any,
      job: jobId.id,
    });

    switch (res.response.type) {
      case 'sync':
        jobId.discard();
        if (autoKillTimer !== null) {
          clearTimeout(autoKillTimer);
        }
        resolve({ type: 'ok', value: res.response.value as Res });
        break;

      case 'job':
        autoKillTimer = setTimeout(() => {
          env.performTypedRpc<StopJobReq, StopJobRes>({
            type: 'Concurrent:StopJob',
            job: jobId.id,
          }).then(res => {
            jobId.discard();
            if (res.err) {
              console.warn('Error when stopping job:', res.err);
              return false;
            }
            return true;
          });
          resolve({ type: 'timeout' });
        }, env.autoAsyncTimeout);
        break;
    }
  } catch (e) {
    reject(e);
  }
});

export default evaluateAsyncRequest;


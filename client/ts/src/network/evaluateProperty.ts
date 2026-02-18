import ModalEnv, { JobId } from '../model/ModalEnv';
import { EvaluatePropertyReq, EvaluatePropertyRes, PollWorkerStatusReq, PollWorkerStatusRes, StopJobReq, StopJobRes, SynchronousEvaluationResult } from '../protocol';


interface OngoingPropertyEvaluation {
  cancel: () => void,
  fetch: () =>  Promise<SynchronousEvaluationResult | 'stopped'>,
}
const evaluateProperty = (
  env: ModalEnv,
  req: Omit<EvaluatePropertyReq, 'job'>,
  onSlowResponseDetected?: () => void,
  onStatusUpdate?: (status: string, stackTrace: string[] | null) => void,
  cleanupSlownessInformation?: () => void, // Only called if onSlowResponseDetected
): OngoingPropertyEvaluation  => {

  let cancelled = false;
  let activelyLoadingJob: JobId | null = null;
  const doStopJob = (jobId: JobId) => env.performTypedRpc<StopJobReq, StopJobRes>({
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

  let stopper = () => {};
  return {
    cancel: () => {
      cancelled = true;
      if (activelyLoadingJob !== null) {
        doStopJob(activelyLoadingJob);
        activelyLoadingJob = null;
      }
      stopper();
    },
    fetch: () => new Promise((resolve, reject) => {

      stopper = () => resolve('stopped');
      let isDone = false;
      let isConnectedToConcurrentCapableServer = false;
      let knownStatus = 'Unknown';
      let knownStackTrace: string[] | null = null;
      let localConcurrentCleanup = () => {};
      const initialPollDelayTimer = setTimeout(() => {
        if (isDone || cancelled || !isConnectedToConcurrentCapableServer) {
          return;
        }
        onSlowResponseDetected?.();

        localConcurrentCleanup = () => { cleanupSlownessInformation?.(); };
        const poll = () => {
          if (isDone || cancelled) {
            return;
          }
          env.performTypedRpc<PollWorkerStatusReq, PollWorkerStatusRes>({
            type: 'Concurrent:PollWorkerStatus',
            job: jobId.id,
          })
            .then(res => {
              if (res.ok) {
                // Polled OK, async update will be delivered to job monitor below
                // Queue future poll.
                setTimeout(poll, 1000);
              } else {
                console.warn('Error when polling for job status');
                // Don't queue more polling, very unlikely to work anyway.
              }
            });
        };
        poll();
      }, 5000);

      const jobId = env.createJobId(update => {
        switch (update.value.type) {
          case 'status': {
            knownStatus = update.value.value;
            onStatusUpdate?.(knownStatus, knownStackTrace);
            break;
          }
          case 'workerStackTrace': {
            knownStackTrace = update.value.value;
            onStatusUpdate?.(knownStatus, knownStackTrace);
            break;
          }

          case 'workerTaskDone': {
            const wtd = update.value.value;
            switch (wtd.type) {
              case 'normal': {
                isDone = true;
                activelyLoadingJob = null;
                localConcurrentCleanup();
                const cast = wtd.value as EvaluatePropertyRes;
                if (cast.response.type == 'job') {
                  throw new Error(`Unexpected 'job' result in async update`);
                }
                resolve(cast.response.value);
                break;
              }

            }
            break;
          }
        }
      });

      env.performTypedRpc<EvaluatePropertyReq, EvaluatePropertyRes>({
        ...req,
        job: jobId.id,
        jobLabel: `Probe: '${`${req.locator.result.label ?? req.locator.result.type}`.split('.').slice(-1)[0]}.${req.property.name}'`,
      })
        .then((data) => {
          if (data.response.type === 'job') {
            // Async work queued, not done.
            if (cancelled) {
              // We were removed while this request was sent
              // Stop it asap
              doStopJob(jobId);
              clearTimeout(initialPollDelayTimer);
            } else {
              activelyLoadingJob = jobId;
            }
            isConnectedToConcurrentCapableServer = true;
          } else {
            // Sync work executed, done.
            clearTimeout(initialPollDelayTimer);
            jobId.discard();
            isDone = true;
            resolve(data.response.value);
          }
        })
    }),
  };
}

export { OngoingPropertyEvaluation }
export default evaluateProperty;

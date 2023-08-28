import { Property, Diagnostic, EvaluatePropertyReq, EvaluatePropertyRes, NodeLocator } from '../protocol';
import settings from '../settings';
import ModalEnv from './ModalEnv';

const runInvisibleProbe = (env: ModalEnv, locator: NodeLocator, property: Property) => {
  const id = `invisible-probe-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;
  const localErrors: Diagnostic[] = [];
  env.probeMarkers[id] = localErrors;

  let state: 'loading' | 'idle' = 'idle';
  let reloadOnDone = false;
  const onRpcDone = () => {
    state = 'idle';
    if (reloadOnDone) {
      reloadOnDone = false;
      performRpc();
    }
  }
  const performRpc = () => {
    state = 'loading';
    env.performTypedRpc<EvaluatePropertyReq, EvaluatePropertyRes>({
      type: 'EvaluateProperty',
      property,
      locator,
      src: env.createParsingRequestData(),
      captureStdout: settings.shouldCaptureStdio(),
      // No need to capture tracing information in background probes
    })
      .then((rawResp) => {
        if (rawResp.response.type === 'job') {
          throw new Error(`Got concurrent response for non-concurrent request`);
        }
        const res = rawResp.response.value;

        const prevLen = localErrors.length;
        localErrors.length = 0;
        localErrors.push(...(res.errors ?? []));
        if (prevLen !== 0 || localErrors.length !== 0) {
          env.updateMarkers();
        }
        onRpcDone();
      })
      .catch((err) => {
        console.warn('Failed refreshing invisible probe', err);
        onRpcDone();
      });
  };

  const refresh = () => {
    if (state === 'loading') {
      reloadOnDone = true;
    } else {
      performRpc();
    }
  };
  env.onChangeListeners[id] = refresh;
  refresh();
};

export default runInvisibleProbe;

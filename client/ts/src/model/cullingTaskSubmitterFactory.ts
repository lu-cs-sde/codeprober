import ModalEnv from './ModalEnv';

const createCullingTaskSubmitter = (cullTime: number | any): CullingTaskSubmitter => {
  if (typeof cullTime !== 'number') {
    return {
      submit: (cb) => cb(),
      cancel: () => {},
      fireImmediately: async () => {},
    };
  }
  let localChangeDebounceTimer: any = -1;
  let pendingTask:
      { type: 'not-started', func: (() => any) }
    | { type: 'running', promise: Promise<any> }
    | null = null;
  const submit: CullingTaskSubmitter['submit'] = (cb) => {
    clearTimeout(localChangeDebounceTimer);
    pendingTask = { type: 'not-started', func: cb };
    localChangeDebounceTimer = setTimeout(async () => {
      const promise: Promise<any> = cb() as any;
      pendingTask = { type: 'running', promise, };
      await promise;
      pendingTask = null;
    }, cullTime);
  };
  const cancel: CullingTaskSubmitter['cancel'] = () => {
    clearTimeout(localChangeDebounceTimer);
    pendingTask = null;
  };
  const fireImmediately: CullingTaskSubmitter['fireImmediately'] = async () => {
    const pt = pendingTask;
    pendingTask = null;
    clearTimeout(localChangeDebounceTimer);

    if (pt) {
      if (pt.type == 'not-started') {
        await pt.func();
      } else {
        await pt.promise;
      }
    }
  };
  return {
    submit,
    cancel,
    fireImmediately,
  };
}
const createCullingTaskSubmitterFactory: (timeout: any | number) => ModalEnv['createCullingTaskSubmitter'] = (cullTime) => {
  if (typeof cullTime === 'number') {
    return (minDelay) => createCullingTaskSubmitter(Math.max(cullTime, minDelay ?? cullTime));
  }
  return createCullingTaskSubmitter;
};

export default createCullingTaskSubmitterFactory;

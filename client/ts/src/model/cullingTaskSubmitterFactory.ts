import ModalEnv from './ModalEnv';

const createCullingTaskSubmitterFactory: (timeout: any | number) => ModalEnv['createCullingTaskSubmitter'] = (cullTime) => {
  if (typeof cullTime !== 'number') {
    return () => ({ submit: (cb) => cb(), cancel: () => {}, fireImmediately: async () => {}, });
  }
  return () => {
    let localChangeDebounceTimer: any = -1;
    let pendingTask: (() => any) | null = null;
    const submit: CullingTaskSubmitter['submit'] = (cb) => {
      clearTimeout(localChangeDebounceTimer);
      pendingTask = cb;
      localChangeDebounceTimer = setTimeout(() => {
        cb();
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
        await pt();
      }
    };
    return { submit, cancel, fireImmediately, };
  }
};

export default createCullingTaskSubmitterFactory;

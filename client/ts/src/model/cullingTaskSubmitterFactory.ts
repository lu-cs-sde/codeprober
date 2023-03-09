import ModalEnv from './ModalEnv';


const createCullingTaskSubmitterFactory: (timeout: any | number) => ModalEnv['createCullingTaskSubmitter'] = (cullTime) => {
  if (typeof cullTime !== 'number') {
    return () => ({ submit: (cb) => cb(), cancel: () => {}, });
  }
  return () => {
    let localChangeDebounceTimer: any = -1;
    return {
      submit: (cb) => {
        clearTimeout(localChangeDebounceTimer);
        localChangeDebounceTimer = setTimeout(() => cb(), cullTime);
      },
      cancel: () => {
        clearTimeout(localChangeDebounceTimer);
      },
    };
  }
};

export default createCullingTaskSubmitterFactory;

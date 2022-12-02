

const createCullingTaskSubmitterFactory: (timeout: any | number) => ModalEnv['createCullingTaskSubmitter'] = (cullTime) => {
  console.log('create taskSubmitter, cullTime: ', cullTime, typeof cullTime);
  if (typeof cullTime !== 'number') {
    return () => ({ submit: (cb) => cb(), });
  }
  return () => {
    let localChangeDebounceTimer: any = -1;
    return {
      submit: (cb) => {
        clearTimeout(localChangeDebounceTimer);
        localChangeDebounceTimer = setTimeout(() => cb(), cullTime);
      }
    };
  }
};

export default createCullingTaskSubmitterFactory;

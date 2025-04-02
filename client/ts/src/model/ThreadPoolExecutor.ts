
interface ThreadPoolExecutor {
  submit: (func: () => Promise<any>) => void;
  wait: () => Promise<any>;
}

const createThreadPoolExecutor = (limitThreads: number): ThreadPoolExecutor => {
  let activeTasks = 0;
  let cachedSlotWaiter: Promise<any> | null = null;
  let cachedSlotResolver: () => void = () => {};
  const waitForSlot = async (): Promise<any> => {
    if (activeTasks < limitThreads) {
      ++activeTasks;
      return;
    }
    if (!cachedSlotWaiter) {
      let preResolved = false;
      cachedSlotResolver = () => {
        preResolved = true;
      }
      cachedSlotWaiter = new Promise((resolve => {
        if (preResolved) {
          resolve('');
        } else {
          cachedSlotResolver = () => resolve('');
        }
      })).then(() => {
        cachedSlotWaiter = null;
      });
    }
    return cachedSlotWaiter.then(waitForSlot);
  }

  const submittedTasks: { [taskId: string]: Promise<any> } = {};
  let taskIdGen = 0;
  const submit: ThreadPoolExecutor['submit'] = async (func) => {
    const taskId = `t${taskIdGen++}`;
    submittedTasks[taskId] = (async () => {
      await waitForSlot();
      try {
        await func();
      } catch (e) {
        console.warn('Failed task', taskId, ':', e);
      }
      --activeTasks;
      delete submittedTasks[taskId];
      cachedSlotResolver();
    })();
  };

  const wait: ThreadPoolExecutor['wait'] = async () => {
    const remaining = Object.values(submittedTasks);
    if (remaining.length === 0) {
      return;
    }
    return Promise.all(remaining).then(wait);
  };

  return { submit, wait };
};

export default ThreadPoolExecutor;
export { createThreadPoolExecutor };

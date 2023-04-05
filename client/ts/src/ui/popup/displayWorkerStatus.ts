import ModalEnv, { JobId } from '../../model/ModalEnv';
import StatisticsCollectorImpl from "../../model/StatisticsCollectorImpl";
import subscribeToWorkerStatus from '../../rpc/subscribeToWorkerStatus';
import unsubscribeFromWorkerStatus from '../../rpc/unsubscribeFromWorkerStatus';
import createModalTitle from "../create/createModalTitle";
import showWindow from "../create/showWindow";

const displayWorkerStatus = (
  env: ModalEnv,
  setDisplayButtonDisabled: (disabled: boolean) => void,
): void => {
  let activeSubscription: { job: JobId, subscriberId: number} | null = null;

  const onClose = () => {
    helpWindow.remove();
    setDisplayButtonDisabled(false);

    if (activeSubscription != null) {
      unsubscribeFromWorkerStatus(env, {
        query: {
          attr: { name: 'meta:unsubscribeFromWorkerStatus', },
        },
        type: 'query',
        job: activeSubscription.job,
        subscriberId: activeSubscription.subscriberId,
      });
    }
  };
  setDisplayButtonDisabled(true);

  const helpWindow = showWindow({
    rootStyle: `
      width: 32rem;
      min-height: 12rem;
    `,
    resizable: true,
    render: (root) => {
      while (root.firstChild) root.removeChild(root.firstChild);

      root.appendChild(createModalTitle({
        renderLeft: (container) => {
          container.appendChild(document.createTextNode('Worker status'));
        },
        onClose,
      }).element);

      const pre = document.createElement('pre');
      root.appendChild(pre);
      pre.innerText = `Requesting worker status..`;

      const job = env.createJobId(data => {
        console.log('worker status data:', data);
        if (!data.workers) {
          return;
        }
        // pre.innerText = `status: ${JSON.stringify(data. null, data)}`;
        pre.innerText = `Status:\n${(data.workers as string[]).map((stat, idx) => `${`${idx + 1}`.padStart(3, ' ')}:${stat}`).join('\n')}`
      });
      // activeJob = job;

      subscribeToWorkerStatus(env, {
        query: { attr: { name: 'meta:subscribeToWorkerStatus' } },
        type: 'query',
        job,
      }).then(res => {
        activeSubscription = {
          job,
          subscriberId: res.subscriberId,
        };
      });
   },
  });
}

export default displayWorkerStatus;

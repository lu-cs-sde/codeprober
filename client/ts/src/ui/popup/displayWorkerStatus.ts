import ModalEnv, { JobId } from '../../model/ModalEnv';
import { SubscribeToWorkerStatusReq, SubscribeToWorkerStatusRes, UnsubscribeFromWorkerStatusReq, UnsubscribeFromWorkerStatusRes } from '../../protocol';
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
      activeSubscription.job.discard();
      env.performTypedRpc<UnsubscribeFromWorkerStatusReq, UnsubscribeFromWorkerStatusRes>({
        type: 'Concurrent:UnsubscribeFromWorkerStatus',
        job: activeSubscription.job.id,
        subscriberId: activeSubscription.subscriberId,
      }).then(res => {
        if (!res.ok) {
          console.warn('Failed unsubscribing from worker status for', activeSubscription);
        }
      })
    }
  };
  setDisplayButtonDisabled(true);

  const helpWindow = showWindow({
    rootStyle: `
      width: 32rem;
      min-height: 12rem;
    `,
    resizable: true,
    onForceClose: onClose,
    render: (root) => {
      while (root.firstChild) root.removeChild(root.firstChild);

      root.appendChild(createModalTitle({
        renderLeft: (container) => {
          container.appendChild(document.createTextNode('Worker status monitor'));
        },
        onClose,
      }).element);

      const pre = document.createElement('pre');
      pre.style.margin = '0.25rem';
      root.appendChild(pre);
      pre.innerText = `Requesting worker status..`;

      const job = env.createJobId(data => {
        // console.log('worker status data:', data);
        switch (data.value.type) {
          case 'workerStatuses': {
            const workers = data.value.value
            pre.innerText = `Status:\n${workers.map((stat, idx) => `${`${idx + 1}`.padStart(3, ' ')}:${stat}`).join('\n')}`
            break;
          }
        }
      });

      env.performTypedRpc<SubscribeToWorkerStatusReq, SubscribeToWorkerStatusRes>({
        type: 'Concurrent:SubscribeToWorkerStatus',
        job: job.id,
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

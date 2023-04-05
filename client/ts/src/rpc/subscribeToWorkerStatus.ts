import { JobId } from '../model/ModalEnv';
import { buildRpc } from './Rpc';

export default buildRpc<{
  query: {
    attr: { name: 'meta:subscribeToWorkerStatus'; };
  };
  type: 'query';
  job: JobId;
}, {
  subscriberId: number;
}>();

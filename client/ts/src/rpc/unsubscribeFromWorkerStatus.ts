import { JobId } from '../model/ModalEnv';
import { buildRpc } from './Rpc';

export default buildRpc<{
  query: {
    attr: { name: 'meta:unsubscribeFromWorkerStatus'; };
  };
  type: 'query';
  job: JobId;
  subscriberId: number;
}, {
  ok: boolean;
}>();

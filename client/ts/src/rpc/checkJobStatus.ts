import { JobId } from '../model/ModalEnv';
import { buildRpc } from './Rpc';

export default buildRpc<{
  query: {
    attr: { name: 'meta:checkJobStatus'; };
  };
  type: 'query';
  job: JobId;
}, {
}>();

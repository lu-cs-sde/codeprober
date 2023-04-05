import { JobId } from '../model/ModalEnv';
import { buildRpc } from './Rpc';

export default buildRpc<{
  query: {
    attr: { name: 'meta:stopJob'; };
  };
  type: 'query';
  job: JobId;
}, {
  result: 'stopped'
} | {
  result: 'error';
  message: string;
}>();

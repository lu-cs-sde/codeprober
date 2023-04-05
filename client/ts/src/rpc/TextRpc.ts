import { JobId } from '../model/ModalEnv';
import Rpc, { buildRpc } from './Rpc';

interface TextRpcRequest<Query> {
  posRecovery:  'FAIL' | 'SEQUENCE_PARENT_CHILD' | 'SEQUENCE_CHILD_PARENT' | 'PARENT' | 'CHILD' | 'ALTERNATE_PARENT_CHILD';
  cache: 'FULL' | 'PARTIAL' | 'NONE' | 'PURGE';
  type: 'query';
  text: string;
  stdout: boolean;
  query: Query;
  mainArgs: string[] | null;
  tmpSuffix: string;
  job?: JobId;
}
const buildTextRpc: <Req, Res>() => Rpc<TextRpcRequest<Req>, Res> = () => buildRpc();

type TextRpc<Query, Res> = Rpc<TextRpcRequest<Query>, Res>;

export { TextRpcRequest, buildTextRpc }
export default TextRpc;

import { JobId } from '../model/ModalEnv';
import Rpc, { buildRpc } from './Rpc';

interface TestRpcRequest<Query> {
  type: 'testMeta';
  query: Query;
}
const buildTestRpc: <Req, Res>() => Rpc<TestRpcRequest<Req>, Res> = () => buildRpc();

type TestRpc<Query, Res> = Rpc<TestRpcRequest<Query>, Res>;

export { TestRpcRequest, buildTestRpc }
export default TestRpc;

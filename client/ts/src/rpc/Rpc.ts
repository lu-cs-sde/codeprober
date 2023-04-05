import ModalEnv from '../model/ModalEnv';

type Rpc<Req, Res> = (env: ModalEnv, req: Req) => Promise<Res>;

const buildRpc: <Req, Res>() => Rpc<Req, Res> = () => (env, req) => env.performTypedRpc(req);

export { buildRpc }
export default Rpc;

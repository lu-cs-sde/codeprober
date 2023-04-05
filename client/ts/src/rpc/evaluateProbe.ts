import { JobId } from '../model/ModalEnv';
import { buildTextRpc } from './TextRpc';

interface ConcurrentResponse {
  job: JobId;
};

export interface ProbeResponse {
  job?: undefined;
  body: RpcBodyLine[];
  totalTime?: number;
  parseTime?: number;
  createLocatorTime?: number;
  applyLocatorTime?: number;
  attrEvalTime?: number;
  errors: {
    severity: 'error' | 'warning' | 'info';
    start: number;
    end: number;
    msg: string;
  }[];
  args?: (Omit<AstAttrArg, 'name'> & { value: ArgValue })[];
  locator?: NodeLocator;
}
export default buildTextRpc<{
  attr: AstAttrWithValue;
  locator: NodeLocator;
}, ProbeResponse | ConcurrentResponse>();

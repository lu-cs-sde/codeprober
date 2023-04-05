import { buildTestRpc } from './TestRpc';

export default buildTestRpc<{
  type: 'meta:getTestSuite';
  suite: string;
}, {
  contents?: string;
}>();

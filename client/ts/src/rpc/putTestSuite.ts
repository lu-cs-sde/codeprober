import { buildTestRpc } from './TestRpc';

export default buildTestRpc<{
  type: 'meta:putTestSuite';
  suite: string;
  contents: string;
}, {
  ok?: boolean;
}>();

import { buildTestRpc } from './TestRpc';

export default buildTestRpc<{
  type: 'meta:listTestSuites';
}, {
  suites?: string[];
}>();

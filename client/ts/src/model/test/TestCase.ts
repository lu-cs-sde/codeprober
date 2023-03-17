

// TODO: add more locators, for example one with field names rather than child indexes
interface MultiLocator {
  robust: NodeLocator;
  naive: NodeLocator;
}


interface TestCase {
  type: 'test';
  src: string;
  assert: TestAssertion;
  // category: string; // Short restricted name, file name will be `${category}.json`
  name: string;                 // 'Test name', can be anything as it will not be used as a file name
  locator: MultiLocator;        // Locator to be used for finding the node we observer
  attribute: AstAttrWithValue,  // The attribute to evaluate on that node

  posRecovery:  'FAIL' | 'SEQUENCE_PARENT_CHILD' | 'SEQUENCE_CHILD_PARENT' | 'PARENT' | 'CHILD' | 'ALTERNATE_PARENT_CHILD';
  cache: 'FULL' | 'PARTIAL' | 'NONE' | 'PURGE';
  tmpSuffix: string;
  mainArgs: string[] | null;
}

type AssertionLine = string | MultiLocator  | (AssertionLine[]);
// Exact same content, in same order
interface IdentityAssertion {
  type: 'identity';
  lines: RpcBodyLine[];
}

// Exact same content, any order
interface SetAssertion {
  type: 'set';
  lines: RpcBodyLine[];
}

// Should finish normally, don't look at output
interface SmokeAssertion {
  type: 'smoke';
}

type TestAssertion = IdentityAssertion | SetAssertion | SmokeAssertion;

export default TestCase;
export { MultiLocator, IdentityAssertion, SetAssertion, SmokeAssertion, AssertionLine }

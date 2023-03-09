
interface TwinLocator {
  robust: NodeLocator;
  naive: NodeLocator;
}

interface TestCase {
  type: 'test';
  src: string;
  assert: TestAssertion;
  // category: string; // Short restricted name, file name will be `${category}.json`
  name: string;                // 'Test name', can be anything as it will not be used as a file name
  locator: TwinLocator;        // Locator to be used for finding the node we observer
  attribute: AstAttrWithValue, // The attribute to evaluate on that node
}

type AssertionLine = string | TwinLocator  | (AssertionLine[]);
// Exact same content, in same order
interface IdentityAssertion {
  type: 'identity';
  lines: AssertionLine[];
}

// Exact same content, any order
interface SetAssertion {
  type: 'set';
  lines: AssertionLine[];
}

// Should finish normally, don't look at output
interface SmokeAssertion {
  type: 'smoke';
}

type TestAssertion = IdentityAssertion | SetAssertion | SmokeAssertion;

export default TestCase;
export { TwinLocator, IdentityAssertion, SetAssertion, SmokeAssertion, AssertionLine }

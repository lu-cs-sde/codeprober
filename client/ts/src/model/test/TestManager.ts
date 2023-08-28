import { AsyncRpcUpdate, EvaluatePropertyReq, EvaluatePropertyRes, GetTestSuiteReq, GetTestSuiteRes, ListTestSuitesReq, ListTestSuitesRes, NestedTest, NodeLocator, ParsingRequestData, Property, PutTestSuiteReq, PutTestSuiteRes, RpcBodyLine, SynchronousEvaluationResult, TestCase, WorkerTaskDone } from '../../protocol';
import settings from '../../settings';
import findLocatorWithNestingPath from '../findLocatorWithNestingPath';
import ModalEnv from '../ModalEnv';
import compareTestResult from './compareTestResult';

type ChangeType = 'added-test' | 'updated-test' | 'removed-test' | 'test-status-update';
type ChangeListener = (type: ChangeType) => void;

// interface TestStatusSide {
//   line: RpcBodyLine;
//   statuse: ('ok' | 'different');
//   nested: NestedTestStatus[];
// }

// interface NestedTestStatus {
//   path: number[];
//   expected: TestStatusSide[];
//   actual: TestStatusSide[];
// }
// interface TestStatus extends Omit<NestedTestStatus, 'path'> {
//   overall: 'ok' | 'warn' | 'error';
// };

type TestProblemMarker =
      'error'
      // 'warn'
    // | 'error'
    ;

interface TestMarkers {
  [id: string]: TestProblemMarker;
}
// interface LineMarkers {
//   [id: number]: TestProblemMarker,
// };

// interface NestedMarkers {
//   [id: string]: TestStatus[],
// };

interface TestStatus {
  overall: 'ok' | 'error';
  expectedMarkers: TestMarkers;
  actualMarkers: TestMarkers;
}

interface NestedTestRequest {
  path: number[];
  property: Property;
  nested: NestedTestRequest[];
}
type NestedTestResponse = {
  path: number[];
  property: Property;
  result: 'could-not-find-node' | {
    body: RpcBodyLine[];
    nested: NestedTestResponse[];
  };
}

interface TestEvaluation {
  test: TestCase;
  output: NestedTestResponse;
  status: TestStatus;

}

interface TestManager {
  listTestSuiteCategories: () => Promise<string[] | 'failed-listing'>,
  getTestSuite: (category: string) => Promise<TestCase[] | 'failed-fetching'>;
  evaluateTest: (category: string, name: string) => Promise<TestEvaluation | 'failed-fetching'>;
  addTest: (category: string, test: TestCase, overwriteIfExisting: boolean) => Promise<'ok' | 'already-exists-with-that-name' | 'failed-fetching'>,
  removeTest: (category: string, name: string) => Promise<'ok' | 'no-such-test' | 'failed-fetching'>,
  addListener: (uid: string, callback: ChangeListener) => void,
  removeListener: (uid: string) => void,
  flushTestCaseData: () => void;
  fullyEvaluate: (src: ParsingRequestData, property: Property, locator: NodeLocator, nested: NestedTestRequest[], debugLabel: string) => Promise<NestedTestResponse>;
  convertTestResponseToTest: (tcase: TestCase, resopnse: NestedTestResponse) => TestCase | null;
};

const createTestManager = (getEnv: () => ModalEnv, createJobId: ModalEnv['createJobId']): TestManager =>{
  const suiteListRepo: { [categoryId: string]: TestCase[]} = {};

  const saveCategoryState: (category: string, cases: TestCase[]) => Promise<boolean>  = async (category, cases) => {
    console.log('save', category, '-->', cases);
    console.log('expected bytelen:', JSON.stringify(cases).length);
    const prev = suiteListRepo[category];
    suiteListRepo[category] = cases;
    let resp;
    try {
      resp = await getEnv().performTypedRpc<PutTestSuiteReq, PutTestSuiteRes>({
        type: "Test:PutTestSuite",
        suite: `${category}.json`,
        contents: {
          v: 1,
          cases
        },
      });
    } catch (e) {
      suiteListRepo[category] = prev;
      throw e;
    }
    if (!resp.err) return true;
    console.warn(`Failed saving cases for ${category}, error:`, resp.err);
    return false;
  };

  const listeners: { [id: string]: ChangeListener } = {};
  const notifyListeners = (type: ChangeType) => Object.values(listeners).forEach(cb => cb(type));

  let categoryInvalidationCount = 0;
  const addTest: TestManager['addTest'] = async (category, test, overwriteIfExisting) => {
    const categories = await listTestSuiteCategories();
    if (categories == 'failed-listing') { return 'failed-fetching'; }

    // console.log('todo add', category, '/', test.name);

    const existing = await doGetTestSuite(category);
    let alreadyExisted = false;
    if (existing == 'failed-fetching') {
      // Doesn't exist yet, this is OK
    } else if (existing.some(tc => tc.name == test.name) ) {
      if (!overwriteIfExisting) {
        return 'already-exists-with-that-name';
      }
      alreadyExisted = true;
    }
    await saveCategoryState(category, [...(suiteListRepo[category] || []).filter(tc => tc.name !== test.name), test].sort((a, b) => a.name < b.name ? -1 : 1));
    ++categoryInvalidationCount;
    if (overwriteIfExisting && testStatusRepo[category]) {
      delete testStatusRepo[category][test.name];
    }
    notifyListeners(alreadyExisted ? 'updated-test' : 'added-test');
    return 'ok';
  };
  const removeTest: TestManager['removeTest'] = async (category, name) => {
    const existing = await doGetTestSuite(category);
    if (existing == 'failed-fetching') { return 'failed-fetching'; }


    if (!existing.some(cat => cat.name === name)) {
      return 'no-such-test';
    }
    await saveCategoryState(category, existing.filter(cat => cat.name !== name));
    ++categoryInvalidationCount;
    delete testStatusRepo[category];
    notifyListeners('removed-test');
    return 'ok';
  };
  const listTestSuiteCategories: TestManager['listTestSuiteCategories'] = (() => {
    let categoryLister: Promise<string[] | 'failed-listing'> | null = null;
    let categoryListingVersion = -1;
    return () => {
      if (!categoryLister || categoryInvalidationCount !== categoryListingVersion) {
        categoryListingVersion = categoryInvalidationCount;
        categoryLister = getEnv().performTypedRpc<ListTestSuitesReq, ListTestSuitesRes>({
          type: 'Test:ListTestSuites',
        })
          .then(({ result }) => {
            if (result.type == 'err') {
              console.warn('Failed listing test suites. Error code:', result.value);
              return 'failed-listing';
            }
            return result.value
              .map((suite) => {
                if (!suite.endsWith('.json')) {
                  console.warn(`Unexpected suite file name ${suite}`);
                  return '';
                }
                return suite.slice(0, suite.lastIndexOf('.'));
              })
              .filter(Boolean);
          });
      }
      return categoryLister;
    };
  })();

  const doGetTestSuite: TestManager['getTestSuite'] = async (id) => {
    if (suiteListRepo[id]) { return suiteListRepo[id]; }
    const { result } = await getEnv().performTypedRpc<GetTestSuiteReq, GetTestSuiteRes>({
      type: 'Test:GetTestSuite',
      suite: `${id}.json`,
    });
    if (result.type === 'contents') {
      const cases = result.value.cases;
      cases.sort((a, b) => a.name < b.name ? -1 : 1);
      suiteListRepo[id] = cases;
      return cases;
    }
    console.warn(`Failed to get test suite '${id}'. Error code: ${result.value}`);
    return 'failed-fetching';
  };

  const testStatusRepo: { [category: string]: { [name: string]: Promise<TestEvaluation | 'failed-fetching'> | undefined } } = {};
  const evaluateTest: TestManager['evaluateTest'] = (category, name) => {
    const categoryStatus = testStatusRepo[category] || {};
    testStatusRepo[category] = categoryStatus;

    const existing = categoryStatus[name];
    if (!!existing) {
      return existing;
    }
    const fresh = (async (): Promise<TestEvaluation | 'failed-fetching'> => {
      const suite = await doGetTestSuite(category);
      if (suite === 'failed-fetching') {
        return 'failed-fetching';
      }
      const tcase = suite.find(ent => ent.name === name);
      if (!tcase) {
        console.warn(`No such test case '${name}' in ${category}`);
        return 'failed-fetching';
      }

      const nestedTestToRequest = (test: NestedTest): NestedTestRequest => ({
        path: test.path,
        property: test.property,
        nested: test.nestedProperties.map(nestedTestToRequest),
      });

      const res = await fullyEvaluate(
        tcase.src,
        tcase.property,
        tcase.locator,
        tcase.nestedProperties.map(nestedTestToRequest),
        `${tcase.name}`
      );
      if (tcase.assertType === 'SMOKE') {
        return {
          test: tcase,
          output: res,
          status: {
            overall: 'ok',
            expectedMarkers: {},
            actualMarkers: {},
          },
        };
      }
      return {
        test: tcase,
        output: res,
        status: compareTestResult(
          tcase.assertType,
          testToNestedTestResponse({
            expectedOutput: tcase.expectedOutput,
            nestedProperties: tcase.nestedProperties,
            path: [],
            property: tcase.property,
          }),
          res
        ),
      }
    })();
    categoryStatus[name] = fresh;
    notifyListeners('test-status-update');
    return fresh;
  };

  const addListener: TestManager['addListener'] = (uid, callback) => {
    listeners[uid] = callback;
  };
  const removeListener: TestManager['removeListener'] = (uid) => {
    delete listeners[uid];
  };

  const fullyEvaluate: TestManager['fullyEvaluate'] = async (src, property, locator, nested, debugLabel) => {
    // let ret: NestedTestResponse = {
    //   path: '',
    //   property,
    //   body: [],
    //   nested: [],
    // };

    const rootRes = await new Promise<SynchronousEvaluationResult>(async (resolve, reject) => {
      const handleUpdate = (data: AsyncRpcUpdate) => {
        switch (data.value.type) {
          case 'workerTaskDone': {
            const res = data.value.value;
            if (res.type === 'normal') {
              const cast = res.value as EvaluatePropertyRes;
              if (cast.response.type == 'job') {
                throw new Error(`Unexpected 'job' result in async test update`);
              }
              resolve(cast.response.value);
            } else {
              reject(res.value);
            }
          }
        }
        // if (data.status === 'done') {
        //   resolve(data.result.response.value);
        // }
      };

      const jobId = createJobId(handleUpdate);
      try {
        const env = getEnv();
        const res = await env.performTypedRpc<EvaluatePropertyReq, EvaluatePropertyRes>({
          type: 'EvaluateProperty',
          property,
          locator,
          src,
          captureStdout: true, // settings.shouldCaptureStdio(),
          captureTraces: settings.shouldCaptureTraces() || false,
          flushBeforeTraceCollection: (settings.shouldCaptureTraces() && settings.shouldAutoflushTraces()) || false,
          job: jobId,
          jobLabel: `Test > ${debugLabel}`,
        });
        if (res.response.type === 'sync') {
          // Non-concurrent server, handle request synchronously
          resolve(res.response.value);
        }
      } catch (e) {
        reject(e);
      }
    });

    const nestedTestResponses = await Promise.all(nested.map(async (nest): Promise<NestedTestResponse> => {
      const nestPathParts = nest.path;
      const nestLocator = findLocatorWithNestingPath(nestPathParts, rootRes.body);
      if (!nestLocator) {
        console.warn('Could not find node', nest.path, 'in ', rootRes.body);
        return {
          path: nest.path,
          property: nest.property,
          result: 'could-not-find-node',
        }
      }
      const nestRes = await fullyEvaluate(src, nest.property, nestLocator, nest.nested, debugLabel);
      return {
        ...nestRes,
        path: nest.path,
      };
    }));

    return {
      path: [],
      property,
      result: {
        body: rootRes.body,
        nested: nestedTestResponses,
      },
    };
  };

  const convertTestResponseToTest: TestManager['convertTestResponseToTest'] = (tcase, response) => {
    if (response.result === 'could-not-find-node') {
      return null;
    }
    const convertResToTest = (res: NestedTestResponse): NestedTest => {
      if (res.result === 'could-not-find-node') {
        return { path: res.path, property: res.property, expectedOutput: [{
          type: 'plain', value: 'Error: could not find node',
        }], nestedProperties: []}
      }
      return {
        path: res.path,
        property: res.property,
        expectedOutput: res.result.body,
        nestedProperties: res.result.nested.map(convertResToTest),
      };
    };
    return {
      src: tcase.src,
      assertType: tcase.assertType,
      expectedOutput: response.result.body,
      nestedProperties: response.result.nested.map(convertResToTest),
      property: tcase.property,
      locator: tcase.locator,
      name: tcase.name,
    };
  }

  return {
    addTest,
    removeTest,
    listTestSuiteCategories,
    getTestSuite: doGetTestSuite,
    evaluateTest,
    addListener,
    removeListener,
    flushTestCaseData: () => {
      Object.keys(testStatusRepo).forEach(id => delete testStatusRepo[id]);
      notifyListeners('test-status-update');
    },
    fullyEvaluate,
    convertTestResponseToTest,
  };
};

const testToNestedTestResponse = (src: NestedTest): NestedTestResponse => ({
  path: src.path,
  property: src.property,
  result: {
    body: src.expectedOutput,
    nested: src.nestedProperties.map(testToNestedTestResponse),
  },
});
const nestedTestResponseToTest = (src: NestedTestResponse): NestedTest | null => {
  if (src.result === 'could-not-find-node') {
    return null;
  }
  const nestedProperties: NestedTest[] = [];
  src.result.nested.forEach(nest => {
    const res = nestedTestResponseToTest(nest);
    if (res) {
      nestedProperties.push(res);
    }
  });
  return {
    path: src.path,
    property: src.property,
    expectedOutput: src.result.body,
    nestedProperties,
  }
}
export { createTestManager, ChangeType, NestedTestRequest, NestedTestResponse, TestStatus, TestMarkers, TestProblemMarker, TestEvaluation, nestedTestResponseToTest };
export default TestManager

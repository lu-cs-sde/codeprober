import { WebsocketHandler } from '../../createWebsocketHandler';
import ModalEnv from '../ModalEnv';
import compareTestResult, { TestComparisonReport } from './compareTestResult';
import { rpcLinesToAssertionLines } from './rpcBodyToAssertionLine';
import TestCase from './TestCase';


type ChangeType = 'added-test' | 'test-status-update';
type ChangeListener = (type: ChangeType) => void;

interface TestStatus {
  report: TestComparisonReport;
  lines: RpcBodyLine[];
};
interface TestManager {
  listTestSuiteCategories: () => Promise<string[] | 'failed-listing'>,
  getTestSuite: (category: string) => Promise<TestCase[] | 'failed-fetching'>;
  getTestStatus: (category: string, name: string) => Promise<TestStatus | 'failed-fetching'>;
  addTest: (category: string, test: TestCase) => Promise<'ok' | 'already-exists-with-that-name' | 'failed-fetching'>,
  addListener: (uid: string, callback: ChangeListener) => void,
  removeListener: (uid: string) => void,
  flushTestCaseData: () => void;
};

interface RpcArgs {
  posRecovery: TestCase['posRecovery'];
  cache: TestCase['cache'];
  type: 'query',
  text: string;
  stdout: false;
  query: { [k: string]: string | {} };
  mainArgs: TestCase['mainArgs'];
  tmpSuffix: TestCase['tmpSuffix'];
}

const createTestManager = (performRpcQuery: (props: RpcArgs) => Promise<any>): TestManager =>{

  const performMetaQuery = (props: RpcArgs['query']): Promise<any> => performRpcQuery({
    posRecovery: 'FAIL',
    cache: 'FULL',
    type: 'query',
    text: '',
    stdout: false,
    query: props,
    mainArgs: null,
    tmpSuffix: '',
  });

  const suiteListRepo: { [categoryId: string]: TestCase[]} = {};
  // const

  const saveCategoryState: (category: string, cases: TestCase[]) => Promise<boolean>  = async (category, cases) => {
    console.log('save', category, '-->', cases);
    console.log('expected bytelen:', JSON.stringify(cases).length);
    // const newState = [...(repo[category] || {}),
    suiteListRepo[category] = cases;
    const resp = await performMetaQuery({
      attr: {
        name: "meta:putTestSuite"
      },
      category: `${category}.json`,
      suite: JSON.stringify(cases),
    });
    if (resp.ok) return true;
    console.warn(`Failed saving cases for ${category}`);
    return false;
  };

  const listeners: { [id: string]: ChangeListener } = {};
  const notifyListeners = (type: ChangeType) => Object.values(listeners).forEach(cb => cb(type));

  let categoryInvalidationCount = 0;
  const addTest: TestManager['addTest'] = async (category, test) => {
    const categories = await listTestSuiteCategories();
    if (categories == 'failed-listing') { return 'failed-fetching'; }

    console.log('todo add', category, '/', test.name);
    ++categoryInvalidationCount;

    const existing = await getTestSuite(category);
    if (existing == 'failed-fetching') {
      // Doesn't exist yet, this is OK
    } else if (existing.some(tc => tc.name == test.name)) {
      return 'already-exists-with-that-name';
    }
    await saveCategoryState(category, [...(suiteListRepo[category] || []), test]);
    delete testStatusRepo[category];
    notifyListeners('added-test');
    return 'ok';
  };
  const listTestSuiteCategories: TestManager['listTestSuiteCategories'] = (() => {
    let categoryLister: Promise<string[] | 'failed-listing'> | null = null;
    let categoryListingVersion = -1;
    return () => {
      if (!categoryLister || categoryInvalidationCount !== categoryListingVersion) {
        categoryListingVersion = categoryInvalidationCount;
        categoryLister = performMetaQuery({ attr: { name: 'meta:listTestSuites' }, })
          .then(({ suites }) => {
            if (!suites) {
              return 'failed-listing';
            }
            return (suites as string[])
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

  const getTestSuite: TestManager['getTestSuite'] = async (id) => {
    if (suiteListRepo[id]) { return suiteListRepo[id]; }
    const { suite } = await performMetaQuery({
      attr: {
        name: 'meta:getTestSuite',
      },
      category: `${id}.json`,
    });
    if (suite) {
      try {
        const ret = JSON.parse(suite.trim());
        suiteListRepo[id] = ret;
        return ret;
      } catch (e) {
        console.warn(e);
        console.warn(`Suite contents for ${id} is not a valid json string`);
        console.warn('-->', suite);
        return 'failed-fetching';
      }
    }
    console.warn(`Failed to get test suite '${id}'`);
    return 'failed-fetching';
  };

  const testStatusRepo: { [category: string]: { [name: string]: Promise<TestStatus | 'failed-fetching'> | undefined } } = {};
  const getTestStatus: TestManager['getTestStatus'] = (category, name) => {
    const categoryStatus = testStatusRepo[category] || {};
    testStatusRepo[category] = categoryStatus;

    const existing = categoryStatus[name];
    if (!!existing) {
      return existing;
    }

    const fresh: Promise<TestStatus | 'failed-fetching'> = (async () => {
      const suite = await getTestSuite(category);
      if (suite === 'failed-fetching') {
        return 'failed-fetching';
      }
      const tcase = suite.find(ent => ent.name === name);
      if (!tcase) {
        console.warn(`No such test case '${name}' in ${category}`);
        return 'failed-fetching';
      }

      const res = await performRpcQuery({
        posRecovery: tcase.posRecovery,
        cache: tcase.cache,
        type: 'query',
        text: tcase.src,
        stdout: false,
        query: {
          attr: tcase.attribute,
          locator: tcase.locator.robust,
        },
        mainArgs: null,
        tmpSuffix: tcase.tmpSuffix,
      });
      console.log('tcase raw res:', res);
      const report = compareTestResult(tcase, rpcLinesToAssertionLines(res.body))
      notifyListeners('test-status-update');
      return {
        report,
        lines: res.body,
      }
      // await new Promise(res => setTimeout(res, 1000));
      // return 'pass';
    })();
    categoryStatus[name] = fresh;
    return fresh;
  };

  // env.onChangeListeners[`test-manager-${(Math.random() * Number.MAX_SAFE_INTEGER)|0}`] = () => {
  //   Object.keys(testStatusRepo).forEach(id => delete testStatusRepo[id]);
  //   notifyListeners('test-status-update');
  // };

  const addListener: TestManager['addListener'] = (uid, callback) => {
    listeners[uid] = callback;
  };
  const removeListener: TestManager['removeListener'] = (uid) => {
    delete listeners[uid];
  };

  return {
    addTest,
    listTestSuiteCategories,
    getTestSuite,
    getTestStatus,
    addListener,
    removeListener,
    flushTestCaseData: () => {
      Object.keys(testStatusRepo).forEach(id => delete testStatusRepo[id]);
      notifyListeners('test-status-update');
    },
    // onSourceTextChange,
  };
};

export { createTestManager, ChangeType };
export default TestManager

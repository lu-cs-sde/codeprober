import evaluateProbe from '../../rpc/evaluateProbe';
import getTestSuite from '../../rpc/getTestSuite';
import listTestSuites from '../../rpc/listTestSuites';
import putTestSuite from '../../rpc/putTestSuite';
import ModalEnv from '../ModalEnv';
import compareTestResult, { TestComparisonReport } from './compareTestResult';
import { rpcLinesToAssertionLines } from './rpcBodyToAssertionLine';
import TestCase from './TestCase';


type ChangeType = 'added-test' | 'removed-test' | 'test-status-update';
type ChangeListener = (type: ChangeType) => void;

interface TestStatus {
  report: TestComparisonReport;
  lines: RpcBodyLine[];
};
interface TestManager {
  listTestSuiteCategories: () => Promise<string[] | 'failed-listing'>,
  getTestSuite: (category: string) => Promise<TestCase[] | 'failed-fetching'>;
  getTestStatus: (category: string, name: string) => Promise<TestStatus | 'failed-fetching'>;
  addTest: (category: string, test: TestCase, overwriteIfExisting: boolean) => Promise<'ok' | 'already-exists-with-that-name' | 'failed-fetching'>,
  removeTest: (category: string, name: string) => Promise<'ok' | 'no-such-test' | 'failed-fetching'>,
  addListener: (uid: string, callback: ChangeListener) => void,
  removeListener: (uid: string) => void,
  flushTestCaseData: () => void;
};

const createTestManager = (getEnv: () => ModalEnv, createJobId: ModalEnv['createJobId']): TestManager =>{
  const suiteListRepo: { [categoryId: string]: TestCase[]} = {};

  const saveCategoryState: (category: string, cases: TestCase[]) => Promise<boolean>  = async (category, cases) => {
    console.log('save', category, '-->', cases);
    console.log('expected bytelen:', JSON.stringify(cases).length);
    // const newState = [...(repo[category] || {}),
    suiteListRepo[category] = cases;
    const resp = await putTestSuite(getEnv(), {
      query: {
        type: "meta:putTestSuite",
        suite: `${category}.json`,
        contents: JSON.stringify(cases),
      },
      type: 'testMeta',
    });
    if (resp.ok) return true;
    console.warn(`Failed saving cases for ${category}`);
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
    if (existing == 'failed-fetching') {
      // Doesn't exist yet, this is OK
    } else if (existing.some(tc => tc.name == test.name) && !overwriteIfExisting) {
      return 'already-exists-with-that-name';
    }
    await saveCategoryState(category, [...(suiteListRepo[category] || []).filter(tc => tc.name !== test.name), test]);
    ++categoryInvalidationCount;
    if (overwriteIfExisting && testStatusRepo[category]) {
      delete testStatusRepo[category][test.name];
    }
    notifyListeners('added-test');
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
        categoryLister = listTestSuites(getEnv(), {
          query: { type: 'meta:listTestSuites', },
          type: 'testMeta',
        })
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

  const doGetTestSuite: TestManager['getTestSuite'] = async (id) => {
    if (suiteListRepo[id]) { return suiteListRepo[id]; }
    const { contents } = await getTestSuite(getEnv(), {
      query: {
        type: 'meta:getTestSuite',
        suite: `${id}.json`,
      },
      type: 'testMeta',
    });
    if (contents) {
      try {
        const ret = JSON.parse(contents.trim());
        suiteListRepo[id] = ret;
        return ret;
      } catch (e) {
        console.warn(e);
        console.warn(`Suite contents for ${id} is not a valid json string`);
        console.warn('-->', contents);
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
      const suite = await doGetTestSuite(category);
      if (suite === 'failed-fetching') {
        return 'failed-fetching';
      }
      const tcase = suite.find(ent => ent.name === name);
      if (!tcase) {
        console.warn(`No such test case '${name}' in ${category}`);
        return 'failed-fetching';
      }

      const res = await new Promise<any>(async (resolve, reject) => {
        const handleUpdate = (data: any) => {
          if (data.status === 'done') {
            resolve(data.result);
          }
        };

        const jobId = createJobId(handleUpdate);
        try {
          const res = await evaluateProbe(getEnv(), {
            type: 'query',
            posRecovery: tcase.posRecovery,
            cache: tcase.cache,
            text: tcase.src,
            stdout: false,
            query: {
              attr: tcase.attribute,
              locator: tcase.locator.robust,
            },
            mainArgs: tcase.mainArgs,
            tmpSuffix: tcase.tmpSuffix,
            job: jobId,
            jobLabel: `Test ${category} > '${tcase.name}'`,
          })
          if (!res.job) {
            // Non-concurrent server, handle request synchronously
            resolve(res);
          }
        } catch (e) {
          reject(e);
        }
      });
      let report: TestComparisonReport;
      if (!res.locator) {
        report = 'failed-eval';
      } else {
        report = compareTestResult(tcase, { locator: res.locator, lines: rpcLinesToAssertionLines(res.body) })
      }
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
    removeTest,
    listTestSuiteCategories,
    getTestSuite: doGetTestSuite,
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

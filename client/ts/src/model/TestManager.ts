import { WebsocketHandler } from '../createWebsocketHandler';
import TestCase from './TestCase';



interface TestManager {
  listCategories: () => Promise<string[] | 'failed-listing'>,
  addTest: (category: string, test: TestCase) => void,
};

const createTestManager = (performRpcQuery: (props: { [k: string]: string | {} }) => Promise<any>): TestManager =>{
  let categoryLister = performRpcQuery({ attr: { name: 'meta:listObservers' }, }).then(({ observers }) => {
    if (!observers) {
      return 'failed-listing';
    }
    return observers;
  });

  // let cateogoryLister = performRpcQuery({
  //   attr: { name: 'meta:getObserver' },
  //   observerId: 'T1.json',
  // }).then(({ observer }) => {
  //   if (!observer) {
  //     console.warn('Failed reading observer data');
  //     return;
  //   }
  //   try {
  //     let parsed: BackgroundObserver[] = JSON.parse(observer);

  //     console.log('obs data:', parsed);

  //   } catch (e) {
  //     console.warn('invalid observer data', e);
  //   }
  // });
  // const listing = wsh.sendRpc
  return {
    addTest: (category, test) => {
      console.log('todo add', category, '/', test.name);
    },
    listCategories: () => categoryLister,
  };
};

export { createTestManager };
export default TestManager

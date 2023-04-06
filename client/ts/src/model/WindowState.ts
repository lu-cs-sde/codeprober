import { NodeLocator, Property } from '../protocol';

interface WindowStateDataProbe {
  type: 'probe';
  locator: NodeLocator;
  property: Property;
  nested: NestedWindows;
}
interface WindowStateDataAst {
  type: 'ast';
  locator: NodeLocator;
  direction: 'upwards' | 'downwards';
  transform: { [id: string]: number },
}

type WindowStateData = WindowStateDataProbe | WindowStateDataAst;

interface WindowState {
  modalPos: ModalPosition;
  data: WindowStateData;
}

type NestedWindows = { [key: string]: WindowState[] };

export { WindowStateData, WindowStateDataProbe, WindowStateDataAst, NestedWindows };
export default WindowState;

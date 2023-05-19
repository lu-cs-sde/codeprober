import { NodeLocator, Property } from '../protocol';

interface WindowStateDataProbe {
  type: 'probe';
  locator: NodeLocator;
  property: Property;
  nested: NestedWindows;
  showDiagnostics?: boolean;
}
interface WindowStateDataAst {
  type: 'ast';
  locator: NodeLocator;
  direction: 'upwards' | 'downwards';
  transform: { [id: string]: number },
}
interface WindowStateDataMinimized {
  type: 'minimized-probe';
  data: WindowStateDataProbe;
}

type WindowStateData = WindowStateDataProbe | WindowStateDataAst | WindowStateDataMinimized;

interface WindowState {
  modalPos: ModalPosition;
  data: WindowStateData;
}

type NestedWindows = { [key: string]: WindowState[] };

export { WindowStateData, WindowStateDataProbe, WindowStateDataAst, NestedWindows };
export default WindowState;

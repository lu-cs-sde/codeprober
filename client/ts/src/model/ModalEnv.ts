import TestManager from './test/TestManager';
import { AsyncRpcUpdate, Diagnostic, ParsingRequestData } from '../protocol';
import { ShowWindowArgs, ShowWindowResult } from '../ui/create/showWindow';
import WindowState from './WindowState';

type JobId = number;

interface ModalEnv {
  showWindow: (args: ShowWindowArgs) => ShowWindowResult;
  performTypedRpc: <Req, Res>(req: Req) => Promise<Res>;
  createParsingRequestData: () => ParsingRequestData;
  getLocalState: () => string;
  setLocalState: (val: string) => void;
  updateSpanHighlight: (span: Span | null) => void;
  setStickyHighlight: (probeId: string, hl: StickyHighlight) => void;
  clearStickyHighlight: (probeId: string) => void;
  probeMarkers: { [probeId: string]: Diagnostic[] };
  onChangeListeners: { [key: string]: (adjusters?: LocationAdjuster[], reason?: string) => void };
  themeChangeListeners: { [key: string]: (light: boolean) => void };
  themeIsLight: () => boolean,
  probeWindowStateSavers: { [key: string]: (target: WindowState[]) => void };
  triggerWindowSave: () => void;
  registerStickyMarker: (initialSpan: Span) => StickyMarker;
  updateMarkers: () => void;
  captureStdout: () => boolean;
  duplicateOnAttr: () => boolean;
  statisticsCollector: ProbeStatisticsCollector;
  currentlyLoadingModals: Set<string>;
  createCullingTaskSubmitter: () => CullingTaskSubmitter;
  testManager: TestManager;
  createJobId: (updateHandler: (data: AsyncRpcUpdate) => void) => JobId;
  getGlobalModalEnv: () => ModalEnv;
}

export { JobId }
export default ModalEnv;

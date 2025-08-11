import TestManager from './test/TestManager';
import { AsyncRpcUpdate, ParsingRequestData } from '../protocol';
import { ShowWindowArgs, ShowWindowResult } from '../ui/create/showWindow';
import WindowState, { WindowStateDataProbe } from './WindowState';
import SourcedDiagnostic from './SourcedDiagnostic';

type JobId = number;

interface ModalEnv {
  showWindow: (args: ShowWindowArgs) => ShowWindowResult;
  putWorkspaceContent: (path: string, contents: string) => void;
  performTypedRpc: <Req, Res>(req: Req) => Promise<Res>;
  createParsingRequestData: () => ParsingRequestData;
  getLocalState: () => string;
  setLocalState: (val: string) => void;
  updateSpanHighlight: (span: Span | null) => void;
  setStickyHighlight: (probeId: string, hl: StickyHighlight) => void;
  clearStickyHighlight: (probeId: string) => void;
  probeMarkers: { [probeId: string]: (() => SourcedDiagnostic[]) | SourcedDiagnostic[] };
  onChangeListeners: { [key: string]: (adjusters?: LocationAdjuster[], reason?: string) => void };
  themeChangeListeners: { [key: string]: (light: boolean) => void };
  themeIsLight: () => boolean,
  probeWindowStateSavers: { [key: string]: (target: WindowState[]) => void };
  triggerWindowSave: () => void;
  updateMarkers: () => void;
  captureStdout: () => boolean;
  duplicateOnAttr: () => boolean;
  statisticsCollector: ProbeStatisticsCollector;
  currentlyLoadingModals: Set<string>;
  createCullingTaskSubmitter: () => CullingTaskSubmitter;
  testManager: TestManager;
  createJobId: (updateHandler: (data: AsyncRpcUpdate) => void) => JobId;
  getGlobalModalEnv: () => ModalEnv;
  minimize: (data: WindowStateDataProbe) => void;
  workerProcessCount: number | undefined;
}

export { JobId }
export default ModalEnv;

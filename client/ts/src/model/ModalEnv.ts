import TestManager from './test/TestManager';
import Rpc from '../rpc/Rpc'
import { TextRpcRequest } from '../rpc/TextRpc';

type JobId = number;

interface ModalEnv {
  performTypedRpc: <Req, Res>(req: Req) => Promise<Res>;
  wrapTextRpc: <Query>(req: { query: Query, jobId?: JobId }) => TextRpcRequest<Query>;
  getLocalState: () => string;
  setLocalState: (val: string) => void;
  updateSpanHighlight: (span: Span | null) => void;
  setStickyHighlight: (probeId: string, hl: StickyHighlight) => void;
  clearStickyHighlight: (probeId: string) => void;
  probeMarkers: { [probeId: string]: ProbeMarker[] };
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
  createJobId: (updateHandler: (data: any) => void) => JobId;
}

export { JobId }
export default ModalEnv;

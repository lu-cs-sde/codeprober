import TestManager from './test/TestManager';

interface ModalEnv {
  performRpcQuery: (args: {
    attr: AstAttrWithValue;
    locator: NodeLocator;
  }) => Promise<any>;
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
}

export default ModalEnv;

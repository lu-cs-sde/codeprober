
import { GetDecorationsReq, GetDecorationsRes, ParsingRequestData } from '../protocol';
import settings from '../settings';
import startEndToSpan from '../ui/startEndToSpan';
import evaluateAsyncRequest from './evaluateAsyncRequest';
import ModalEnv from './ModalEnv'
import SourcedDiagnostic from './SourcedDiagnostic';

interface TextProbeManagerArgs {
  env: ModalEnv;
  onFinishedCheckingActiveFile: (res: TextProbeCheckResults) => void;
}

interface TextProbeCheckResults {
  numPass: number;
  numFail: number;
}

interface TextProbeManager {
  // Nothing here, the manager manages itself
};

type TextProbeStyle = 'angle-brackets' | 'disabled';

const getDecorations = (env: ModalEnv, prd: ParsingRequestData) => {
  return evaluateAsyncRequest<GetDecorationsReq, GetDecorationsRes>(env, { type: 'ide:decorations', src: prd, });
}

const setupTextProbeManager = (args: TextProbeManagerArgs): TextProbeManager => {
  const refreshDispatcher = args.env.createCullingTaskSubmitter()
  const queryId = `query-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;

  const diagnostics: SourcedDiagnostic[] = [];
  args.env.probeMarkers[queryId] = diagnostics;

  let activeRefresh = false;
  let repeatOnDone = false;
  const activeStickies: string[] = [];
  const doRefresh = async () => {
    const hadDiagnosticsBefore = diagnostics.length;
    if (settings.getTextProbeStyle() === 'disabled') {
      for (let i = 0; i < activeStickies.length; ++i) {
        args.env.clearStickyHighlight(activeStickies[i]);
      }
      activeStickies.length = 0;
      args.onFinishedCheckingActiveFile({ numFail: 0, numPass: 0 })
      if (hadDiagnosticsBefore) {
        diagnostics.length = 0;
        args.env.updateMarkers();
      }
      return;
    }
    if (activeRefresh) {
      repeatOnDone = true;
      return;
    }
    activeRefresh = true;
    repeatOnDone = false;
    let nextStickyIndex = 0;
    const allocateStickyId = () => {
      let stickyId;
      if (nextStickyIndex >= activeStickies.length) {
        stickyId = `${queryId}-${nextStickyIndex}`;
        activeStickies.push(stickyId);
      } else {
        stickyId = activeStickies[nextStickyIndex];
      }
      ++nextStickyIndex;
      return stickyId;
    }
    const newDiagnostics: SourcedDiagnostic[] = [];
    function addStickyBox(boxClass: string[], boxSpan: Span, stickyClass: string[], stickyContent: string | null, stickySpan?: Span) {
      args.env.setStickyHighlight(allocateStickyId(), {
        classNames: boxClass,
        span: boxSpan,
      });
      args.env.setStickyHighlight(allocateStickyId(), {
        classNames: [],
        span: stickySpan ?? { ...boxSpan, colStart: boxSpan.colEnd - 3, colEnd: boxSpan.colEnd - 2 },
        content: stickyContent ?? undefined,
        contentClassNames: stickyClass,
      });
    }

    const combinedResults: TextProbeCheckResults = { numPass: 0, numFail: 0 };
    const prd = args.env.createParsingRequestData();
    const res = await getDecorations(args.env, prd);
    const addErrorResult = async (msg: string) => {
      const forcedLines = await args.env.performTypedRpc<GetDecorationsReq, GetDecorationsRes>({
        type: 'ide:decorations',
        src: prd,
        forceAllOK: true
      });
      if (forcedLines.lines?.length) {
        forcedLines.lines.forEach(line => {
          addStickyBox(
            ['elp-result-fail'], startEndToSpan(line.start, line.end + 1 ),
            ['elp-actual-result-err'], msg,
          );
        })
      } else {

        newDiagnostics.push({ type: 'ERROR', start: (1 << 12) + 1, end: (1 << 12) + 0xFFF, msg, source: 'Text Probe' });
      }
      ++combinedResults.numFail;

    }
    switch (res.type) {
      case 'timeout': {
        await addErrorResult(`Timeout when evaluating text probes in this file`);
        break;
      }
      case 'unexpected_error': {
        await addErrorResult(`Error when evaluating text probes in this file: ${res.value?.join(', ')}`);
        break;
      }
      case 'ok': {
        const lines = res.value.lines;
        if (lines) {
          lines.forEach(line => {
            switch (line.type) {
              case 'error': {
                ++combinedResults.numFail;
                const msg = `❌ ${line.message ?? 'Error'}`;
                addStickyBox(
                  ['elp-result-fail'], startEndToSpan(line.start, line.end + 1 ),
                  ['elp-actual-result-err'], msg,
                );
                if (line.contextStart && line.contextEnd) {
                  newDiagnostics.push({ type: 'ERROR', start: line.contextStart, end: line.contextEnd, msg, source: 'Text Probe' });
                }
                break;
              }
              case 'var': {
                args.env.setStickyHighlight(allocateStickyId(), {
                  classNames: ['elp-result-stored'],
                  span: startEndToSpan(line.start, line.end + 1),
                });
                break;
              }
              case 'query': {
                ++combinedResults.numPass;
                addStickyBox(
                  ['elp-result-probe'], startEndToSpan(line.start, line.end + 1 ),
                  ['elp-actual-result-probe'], line.message ?? '=',
                );

                break;
              }
              case 'ok': {
                ++combinedResults.numPass;
                addStickyBox(
                  ['elp-result-success'], startEndToSpan(line.start, line.end + 1 ),
                  ['elp-actual-result-success'], line.message ?? ' ✅',
                );
                break;
              }
              case 'info': {
                addStickyBox(
                  ['elp-result-probe'], startEndToSpan(line.start, line.end + 1 ),
                  ['elp-actual-result-probe'], line.message ?? '=',
                );
                break;
              }
            }
          });
        }
      }
    }

    diagnostics.length = 0;
    diagnostics.push(...newDiagnostics);
    if (diagnostics.length || hadDiagnosticsBefore) {
      args.env.updateMarkers();
    }
    for (let i = nextStickyIndex; i < activeStickies.length; ++i) {
      args.env.clearStickyHighlight(activeStickies[i]);
    }
    activeStickies.length = nextStickyIndex;

    activeRefresh = false;
    args.onFinishedCheckingActiveFile(combinedResults)
    if (repeatOnDone) {
      refresh();
    }
  }

  const refresh = () => refreshDispatcher.submit(doRefresh);

  args.env.onChangeListeners[queryId] = refresh;
  refresh();

  return { }
}

export { setupTextProbeManager, TextProbeCheckResults, TextProbeStyle, getDecorations };
export default TextProbeManager;

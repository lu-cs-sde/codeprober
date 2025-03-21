
type HelpType = 'general' | 'recovery-strategy' | 'probe-window' | 'magic-stdout-messages'
  | 'ast-cache-strategy' | 'syntax-highlighting' | 'main-args-override' | 'customize-file-suffix'
  | 'property-list-usage' | 'show-all-properties' | 'group-properties-by-aspect' | 'duplicate-probe-on-attr' | 'capture-stdout'
  | 'capture-traces' | 'location-style' | 'textprobe-style' | 'ast' | 'test-code-vs-codeprober-code'
  ;


interface Span {
  lineStart: number;
  colStart: number;
  lineEnd: number;
  colEnd: number;
}

type TextMarker = {
  clear?: () => void;
}
type TextMarkFn = (mark: Span & { severity: MarkerSeverity; message: string, source?: string }) => TextMarker;

type LocationAdjuster = (line: number, col: number) => [number, number];

type SyntaxHighlightingLanguageId = 'plaintext' | 'abap' | 'apex' | 'azcli' | 'bat' | 'bicep' | 'cameligo' | 'clojure' | 'coffeescript' | 'c' | 'cpp' | 'csharp' | 'csp' | 'css' | 'dart' | 'dockerfile' | 'ecl' | 'elixir' | 'flow9' | 'fsharp' | 'go' | 'graphql' | 'handlebars' | 'hcl' | 'html' | 'ini' | 'java' | 'javascript' | 'julia' | 'kotlin' | 'less' | 'lexon' | 'lua' | 'liquid' | 'm3' | 'markdown' | 'mips' | 'msdax' | 'mysql' | 'objective-c' | 'pascal' | 'pascaligo' | 'perl' | 'pgsql' | 'php' | 'postiats' | 'powerquery' | 'powershell' | 'proto' | 'pug' | 'python' | 'qsharp' | 'r' | 'razor' | 'redis' | 'redshift' | 'restructuredtext' | 'ruby' | 'rust' | 'sb' | 'scala' | 'scheme' | 'scss' | 'shell' | 'sol' | 'aes' | 'sparql' | 'sql' | 'st' | 'swift' | 'systemverilog' | 'verilog' | 'tcl' | 'twig' | 'typescript' | 'vb' | 'xml' | 'yaml' | 'json';

interface StickyMarker {
  getSpan: () => Span;
  remove: () => void;
}

// Mapping from lineIndex to probes at that location

interface ProbeMeasurement {
  fullRpcMs: number;
  serverSideMs: number;
  serverParseOnlyMs: number;
  serverCreateLocatorMs: number;
  serverApplyLocatorMs: number;
  attrEvalMs: number;
}

interface ProbeStatisticsCollector {
  addProbeEvaluationTime: (measurement: ProbeMeasurement) => void;
};

interface StickyHighlight {
  classNames: string[];
  span: Span;
  content?: string;
  contentClassNames?: string[];
}
interface CullingTaskSubmitter {
  submit: (callback: () => void) => void;
  cancel: () => void;
}

interface ModalPosition { x: number, y: number };

interface Window {
  RagQuery: (line: number, col: number, autoSelectRoot?: boolean) => void;
  displayHelp: (type: HelpType | 'probe-statistics' | 'worker-status') => void;
  initCodeProber: () => void;
  MiniEditorMain: () => void;
  clearUserSettings: () => void;
  saveStateAsUrl: () => void;
  ActiveLocatorRequest?: ActiveNodeLocatorRequest;
  HandleLspLikeInteraction: (type: 'hover' | 'complete', { line: number, column: number }) => Promise<any>;
}

interface ActiveNodeLocatorRequest {
  submit: (locator: NodeLocator) => void;
}



/*
  Battle plan for next week

  1)
  - Refactor away many references to type+span in the modals, replace with 'NodeLocator'

  2)
  - Make server respond with this structure too
      - In the meta info when responding to queries
      - In the 'encodeValue' representation of nodes
   For now, 'steps' is always empty

  3)
  - Implement 'getLocator' in the server.
      - Take the "deepest" (furthest from root) node in [Root,..,OurNode] that has a location
            - If no such node, fail 'getLocator'
      - We now have [Root,..,DeepestWithLoc,..,OurNode]
            - Best case, DeepestWithLoc == OurNode
            - [Root,..,DeepestWithloc] can be ignored.
            - Save and location of of 'DeepestWithLoc' for the response value.
      - Fill [Deepest,..,OurNode] with 'steps'. Happy case: the steps list is empty!
            - For each Parent/Child, encode relation between [P,C].
                - If C exists in the P.children array, make it a 'NodeLocatorChildStep'
                - Else, iterate over all >>cached<< NTA values in P
                    - Identity comparison over them. On match, create a 'NodeLocatorNtaStep'
                    - On zero matches, fail 'getLocator'

  4)
  - Use 'getLocator' instead of creating NodeLocator where 'steps' is always empty

  5)
  - Follow the 'steps' in requests to the server, re-evaluate NTA's as needed

  6)
  - Implement UI explaining this on the client side

  7)
  - Handle errors as best as possible.
    - NodeLocatorChildStep is possibly quite error prone on changes
*/

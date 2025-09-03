interface AsyncRpcUpdate {
  type: "asyncUpdate";
  job: number;
  isFinalUpdate: boolean;
  value: AsyncRpcUpdateValue;
}
type AsyncRpcUpdateValue = (
    { type: 'status'; value: string; }
  | { type: 'workerStackTrace'; value: string[]; }
  | { type: 'workerStatuses'; value: string[]; }
  | { type: 'workerTaskDone'; value: WorkerTaskDone; }
);
interface BackingFile {
  path: string;
  value: string;
}
interface BackingFileUpdated {
  type: "backing_file_update";
  contents: string;
}
interface CompleteReq {
  type: "ide:complete";
  src: ParsingRequestData;
  line: number;
  column: number;
}
interface CompleteRes {
  lines?: string[];
}
interface Diagnostic {
  type: ('ERROR'| 'WARNING'| 'INFO'| 'HINT'| 'LINE_PP'| 'LINE_AA'| 'LINE_AP'| 'LINE_PA');
  start: number;
  end: number;
  msg: string;
}
interface EvaluatePropertyReq {
  type: "EvaluateProperty";
  src: ParsingRequestData;
  locator: NodeLocator;
  property: Property;
  captureStdout: boolean;
  job?: number;
  jobLabel?: string;
  skipResultLocator?: boolean;
  captureTraces?: boolean;
  flushBeforeTraceCollection?: boolean;
  flattenForTextProbes?: boolean;
}
interface EvaluatePropertyRes {
  response: PropertyEvaluationResult;
}
interface FNStep {
  property: Property;
}
interface FindWorkspaceFilesReq {
  type: "FindWorkspaceFiles";
  query: string;
}
interface FindWorkspaceFilesRes {
  matches?: string[];
  truncatedSearch?: boolean;
}
interface GetTestSuiteReq {
  type: "Test:GetTestSuite";
  suite: string;
}
interface GetTestSuiteRes {
  result: TestSuiteOrError;
}
interface GetWorkerStatusReq {
  type: "Concurrent:GetWorkerStatus";
}
interface GetWorkerStatusRes {
  stackTrace: string[];
}
interface GetWorkspaceFileReq {
  type: "GetWorkspaceFile";
  path: string;
}
interface GetWorkspaceFileRes {
  content?: string;
  metadata?: { [key: string]: any };
}
interface HighlightableMessage {
  start: number;
  end: number;
  msg: string;
}
interface HoverReq {
  type: "ide:hover";
  src: ParsingRequestData;
  line: number;
  column: number;
}
interface HoverRes {
  lines?: string[];
}
interface InitInfo {
  type: "init";
  version: {
    hash: string;
    clean: boolean;
    buildTimeSeconds?: number;
  };
  changeBufferTime?: number;
  workerProcessCount?: number;
  disableVersionCheckerByDefault?: boolean;
  backingFile?: BackingFile;
  autoReloadOnDisconnect?: boolean;
  supportsWorkspaceMetadata?: boolean;
}
interface ListNodesReq {
  type: "ListNodes";
  pos: number;
  src: ParsingRequestData;
}
interface ListNodesRes {
  body: RpcBodyLine[];
  nodes?: NodeLocator[];
  errors?: Diagnostic[];
}
interface ListPropertiesReq {
  type: "ListProperties";
  all: boolean;
  locator: NodeLocator;
  src: ParsingRequestData;
}
interface ListPropertiesRes {
  body: RpcBodyLine[];
  properties?: Property[];
}
interface ListTestSuitesReq {
  type: "Test:ListTestSuites";
}
interface ListTestSuitesRes {
  result: TestSuiteListOrError;
}
interface ListTreeReq {
  type: ("ListTreeUpwards" | "ListTreeDownwards");
  locator: NodeLocator;
  src: ParsingRequestData;
}
interface ListTreeRes {
  body: RpcBodyLine[];
  locator?: NodeLocator;
  node?: ListedTreeNode;
}
interface ListWorkspaceDirectoryReq {
  type: "ListWorkspaceDirectory";
  path?: string;
}
interface ListWorkspaceDirectoryRes {
  entries?: WorkspaceEntry[];
}
type ListedTreeChildNode = (
    { type: 'children'; value: ListedTreeNode[]; }
  | { type: 'placeholder'; value: number; }
);
interface ListedTreeNode {
  type: "node";
  locator: NodeLocator;
  name?: string;
  children: ListedTreeChildNode;
  remotes?: NodeLocator[];
}
type LongPollResponse = (
    { type: 'etag'; value: number; }
  | { type: 'push'; value: { [key: string]: any }; }
);
interface NestedTest {
  path: number[];
  property: Property;
  expectedOutput: RpcBodyLine[];
  nestedProperties: NestedTest[];
}
interface NodeContainer {
  node: NodeLocator;
  body?: RpcBodyLine;
}
interface NodeLocator {
  result: TALStep;
  steps: NodeLocatorStep[];
}
type NodeLocatorStep = (
    { type: 'child'; value: number; }
  | { type: 'nta'; value: FNStep; }
  | { type: 'tal'; value: TALStep; }
);
interface NullableNodeLocator {
  type: string;
  value?: NodeLocator;
}
interface ParsingRequestData {
  posRecovery: ('FAIL'| 'SEQUENCE_PARENT_CHILD'| 'SEQUENCE_CHILD_PARENT'| 'PARENT'| 'CHILD'| 'ALTERNATE_PARENT_CHILD');
  cache: ('FULL'| 'PARTIAL'| 'NONE'| 'PURGE');
  src: ParsingSource;
  mainArgs?: string[];
  tmpSuffix: string;
}
type ParsingSource = (
    { type: 'text'; value: string; }
  | { type: 'workspacePath'; value: string; }
);
interface PollWorkerStatusReq {
  type: "Concurrent:PollWorkerStatus";
  job: number;
}
interface PollWorkerStatusRes {
  ok: boolean;
}
interface Property {
  name: string;
  args?: PropertyArg[];
  astChildName?: string;
  aspect?: string;
}
type PropertyArg = (
    { type: 'string'; value: string; }
  | { type: 'integer'; value: number; }
  | { type: 'bool'; value: boolean; }
  | { type: 'collection'; value: PropertyArgCollection; }
  | { type: 'outputstream'; value: string; }
  | { type: 'nodeLocator'; value: NullableNodeLocator; }
);
interface PropertyArgCollection {
  type: string;
  entries: PropertyArg[];
}
type PropertyEvaluationResult = (
    { type: 'job'; value: number; }
  | { type: 'sync'; value: SynchronousEvaluationResult; }
);
interface PutTestSuiteReq {
  type: "Test:PutTestSuite";
  suite: string;
  contents: TestSuite;
}
interface PutTestSuiteRes {
  err?: ('NO_TEST_DIR_SET'| 'ERROR_WHEN_WRITING_FILE');
}
interface PutWorkspaceContentReq {
  type: "PutWorkspaceContent";
  path: string;
  content: string;
}
interface PutWorkspaceContentRes {
  ok: boolean;
}
interface PutWorkspaceMetadataReq {
  type: "PutWorkspaceMetadata";
  path: string;
  metadata?: { [key: string]: any };
}
interface PutWorkspaceMetadataRes {
  ok: boolean;
}
interface Refresh {
  type: "refresh";
}
interface RenameWorkspacePathReq {
  type: "RenameWorkspacePath";
  srcPath: string;
  dstPath: string;
}
interface RenameWorkspacePathRes {
  ok: boolean;
}
type RpcBodyLine = (
    { type: 'plain'; value: string; }
  | { type: 'stdout'; value: string; }
  | { type: 'stderr'; value: string; }
  | { type: 'streamArg'; value: string; }
  | { type: 'arr'; value: RpcBodyLine[]; }
  | { type: 'node'; value: NodeLocator; }
  | { type: 'dotGraph'; value: string; }
  | { type: 'highlightMsg'; value: HighlightableMessage; }
  | { type: 'tracing'; value: Tracing; }
  | { type: 'html'; value: string; }
  | { type: 'nodeContainer'; value: NodeContainer; }
);
interface StopJobReq {
  type: "Concurrent:StopJob";
  job: number;
}
interface StopJobRes {
  err?: string;
}
interface SubmitWorkerTaskReq {
  type: "Concurrent:SubmitTask";
  job: number;
  data: { [key: string]: any };
}
interface SubmitWorkerTaskRes {
  ok: boolean;
}
interface SubscribeToWorkerStatusReq {
  type: "Concurrent:SubscribeToWorkerStatus";
  job: number;
}
interface SubscribeToWorkerStatusRes {
  subscriberId: number;
}
interface SynchronousEvaluationResult {
  body: RpcBodyLine[];
  totalTime: number;
  parseTime: number;
  createLocatorTime: number;
  applyLocatorTime: number;
  attrEvalTime: number;
  listNodesTime: number;
  listPropertiesTime: number;
  errors?: Diagnostic[];
  args?: PropertyArg[];
  locator?: NodeLocator;
}
interface TALStep {
  type: string;
  label?: string;
  start: number;
  end: number;
  depth: number;
  external?: boolean;
}
interface TestCase {
  name: string;
  src: ParsingRequestData;
  property: Property;
  locator: NodeLocator;
  assertType: ('IDENTITY'| 'SET'| 'SMOKE');
  expectedOutput: RpcBodyLine[];
  nestedProperties: NestedTest[];
}
interface TestSuite {
  v: number;
  cases: TestCase[];
}
type TestSuiteListOrError = (
    { type: 'err'; value: ('NO_TEST_DIR_SET'| 'ERROR_WHEN_LISTING_TEST_DIR'); }
  | { type: 'suites'; value: string[]; }
);
type TestSuiteOrError = (
    { type: 'err'; value: ('NO_TEST_DIR_SET'| 'NO_SUCH_TEST_SUITE'| 'ERROR_WHEN_READING_FILE'); }
  | { type: 'contents'; value: TestSuite; }
);
interface TopRequestReq {
  type: "rpc";
  id: number;
  data: { [key: string]: any };
}
interface TopRequestRes {
  type: "rpc";
  id: number;
  data: TopRequestResponseData;
}
type TopRequestResponseData = (
    { type: 'success'; value: { [key: string]: any }; }
  | { type: 'failureMsg'; value: string; }
);
interface Tracing {
  node: NodeLocator;
  prop: Property;
  dependencies: Tracing[];
  result: RpcBodyLine;
}
interface TunneledWsPutRequestReq {
  type: "wsput:tunnel";
  session: string;
  request: { [key: string]: any };
}
interface TunneledWsPutRequestRes {
  response: { [key: string]: any };
}
interface UnlinkWorkspacePathReq {
  type: "UnlinkWorkspacePath";
  path: string;
}
interface UnlinkWorkspacePathRes {
  ok: boolean;
}
interface UnsubscribeFromWorkerStatusReq {
  type: "Concurrent:UnsubscribeFromWorkerStatus";
  job: number;
  subscriberId: number;
}
interface UnsubscribeFromWorkerStatusRes {
  ok: boolean;
}
type WorkerTaskDone = (
    { type: 'normal'; value: { [key: string]: any }; }
  | { type: 'unexpectedError'; value: string[]; }
);
type WorkspaceEntry = (
    { type: 'file'; value: string; }
  | { type: 'directory'; value: string; }
);
interface WorkspacePathsUpdated {
  type: "workspace_paths_updated";
  paths: string[];
}
interface WsPutInitReq {
  type: "wsput:init";
  session: string;
}
interface WsPutInitRes {
  info: InitInfo;
}
interface WsPutLongpollReq {
  type: "wsput:longpoll";
  session: string;
  etag: number;
}
interface WsPutLongpollRes {
  data?: LongPollResponse;
}


export {
   AsyncRpcUpdate
 , AsyncRpcUpdateValue
 , BackingFile
 , BackingFileUpdated
 , CompleteReq
 , CompleteRes
 , Diagnostic
 , EvaluatePropertyReq
 , EvaluatePropertyRes
 , FNStep
 , FindWorkspaceFilesReq
 , FindWorkspaceFilesRes
 , GetTestSuiteReq
 , GetTestSuiteRes
 , GetWorkerStatusReq
 , GetWorkerStatusRes
 , GetWorkspaceFileReq
 , GetWorkspaceFileRes
 , HighlightableMessage
 , HoverReq
 , HoverRes
 , InitInfo
 , ListNodesReq
 , ListNodesRes
 , ListPropertiesReq
 , ListPropertiesRes
 , ListTestSuitesReq
 , ListTestSuitesRes
 , ListTreeReq
 , ListTreeRes
 , ListWorkspaceDirectoryReq
 , ListWorkspaceDirectoryRes
 , ListedTreeChildNode
 , ListedTreeNode
 , LongPollResponse
 , NestedTest
 , NodeContainer
 , NodeLocator
 , NodeLocatorStep
 , NullableNodeLocator
 , ParsingRequestData
 , ParsingSource
 , PollWorkerStatusReq
 , PollWorkerStatusRes
 , Property
 , PropertyArg
 , PropertyArgCollection
 , PropertyEvaluationResult
 , PutTestSuiteReq
 , PutTestSuiteRes
 , PutWorkspaceContentReq
 , PutWorkspaceContentRes
 , PutWorkspaceMetadataReq
 , PutWorkspaceMetadataRes
 , Refresh
 , RenameWorkspacePathReq
 , RenameWorkspacePathRes
 , RpcBodyLine
 , StopJobReq
 , StopJobRes
 , SubmitWorkerTaskReq
 , SubmitWorkerTaskRes
 , SubscribeToWorkerStatusReq
 , SubscribeToWorkerStatusRes
 , SynchronousEvaluationResult
 , TALStep
 , TestCase
 , TestSuite
 , TestSuiteListOrError
 , TestSuiteOrError
 , TopRequestReq
 , TopRequestRes
 , TopRequestResponseData
 , Tracing
 , TunneledWsPutRequestReq
 , TunneledWsPutRequestRes
 , UnlinkWorkspacePathReq
 , UnlinkWorkspacePathRes
 , UnsubscribeFromWorkerStatusReq
 , UnsubscribeFromWorkerStatusRes
 , WorkerTaskDone
 , WorkspaceEntry
 , WorkspacePathsUpdated
 , WsPutInitReq
 , WsPutInitRes
 , WsPutLongpollReq
 , WsPutLongpollRes
}

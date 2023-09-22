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
}
interface EvaluatePropertyRes {
  response: PropertyEvaluationResult;
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
interface ListTestSuitesReq {
  type: "Test:ListTestSuites";
}
interface ListTestSuitesRes {
  result: TestSuiteListOrError;
}
interface GetTestSuiteReq {
  type: "Test:GetTestSuite";
  suite: string;
}
interface GetTestSuiteRes {
  result: TestSuiteOrError;
}
interface PutTestSuiteReq {
  type: "Test:PutTestSuite";
  suite: string;
  contents: TestSuite;
}
interface PutTestSuiteRes {
  err?: ('NO_TEST_DIR_SET'| 'ERROR_WHEN_WRITING_FILE');
}
interface SubscribeToWorkerStatusReq {
  type: "Concurrent:SubscribeToWorkerStatus";
  job: number;
}
interface SubscribeToWorkerStatusRes {
  subscriberId: number;
}
interface UnsubscribeFromWorkerStatusReq {
  type: "Concurrent:UnsubscribeFromWorkerStatus";
  job: number;
  subscriberId: number;
}
interface UnsubscribeFromWorkerStatusRes {
  ok: boolean;
}
interface StopJobReq {
  type: "Concurrent:StopJob";
  job: number;
}
interface StopJobRes {
  err?: string;
}
interface PollWorkerStatusReq {
  type: "Concurrent:PollWorkerStatus";
  job: number;
}
interface PollWorkerStatusRes {
  ok: boolean;
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
interface CompleteReq {
  type: "ide:complete";
  src: ParsingRequestData;
  line: number;
  column: number;
}
interface CompleteRes {
  lines?: string[];
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
interface TunneledWsPutRequestReq {
  type: "wsput:tunnel";
  session: string;
  request: { [key: string]: any };
}
interface TunneledWsPutRequestRes {
  response: { [key: string]: any };
}
interface GetWorkerStatusReq {
  type: "Concurrent:GetWorkerStatus";
}
interface GetWorkerStatusRes {
  stackTrace: string[];
}
interface SubmitWorkerTaskReq {
  type: "Concurrent:SubmitTask";
  job: number;
  data: { [key: string]: any };
}
interface SubmitWorkerTaskRes {
  ok: boolean;
}
type TestSuiteOrError = (
    { type: 'err'; value: ('NO_TEST_DIR_SET'| 'NO_SUCH_TEST_SUITE'| 'ERROR_WHEN_READING_FILE'); }
  | { type: 'contents'; value: TestSuite; }
);
type LongPollResponse = (
    { type: 'etag'; value: number; }
  | { type: 'push'; value: { [key: string]: any }; }
);
interface TestSuite {
  v: number;
  cases: TestCase[];
}
type TopRequestResponseData = (
    { type: 'success'; value: { [key: string]: any }; }
  | { type: 'failureMsg'; value: string; }
);
interface Property {
  name: string;
  args?: PropertyArg[];
  astChildName?: string;
}
interface ParsingRequestData {
  posRecovery: ('FAIL'| 'SEQUENCE_PARENT_CHILD'| 'SEQUENCE_CHILD_PARENT'| 'PARENT'| 'CHILD'| 'ALTERNATE_PARENT_CHILD');
  cache: ('FULL'| 'PARTIAL'| 'NONE'| 'PURGE');
  text: string;
  mainArgs?: string[];
  tmpSuffix: string;
}
interface Diagnostic {
  type: ('ERROR'| 'WARNING'| 'INFO'| 'HINT'| 'LINE_PP'| 'LINE_AA'| 'LINE_AP'| 'LINE_PA');
  start: number;
  end: number;
  msg: string;
}
interface NodeLocator {
  result: TALStep;
  steps: NodeLocatorStep[];
}
type TestSuiteListOrError = (
    { type: 'err'; value: ('NO_TEST_DIR_SET'| 'ERROR_WHEN_LISTING_TEST_DIR'); }
  | { type: 'suites'; value: string[]; }
);
interface Refresh {
  type: "refresh";
}
interface AsyncRpcUpdate {
  type: "asyncUpdate";
  job: number;
  isFinalUpdate: boolean;
  value: AsyncRpcUpdateValue;
}
type PropertyEvaluationResult = (
    { type: 'job'; value: number; }
  | { type: 'sync'; value: SynchronousEvaluationResult; }
);
interface ListedTreeNode {
  type: "node";
  locator: NodeLocator;
  name?: string;
  children: ListedTreeChildNode;
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
);
type PropertyArg = (
    { type: 'string'; value: string; }
  | { type: 'integer'; value: number; }
  | { type: 'bool'; value: boolean; }
  | { type: 'collection'; value: PropertyArgCollection; }
  | { type: 'outputstream'; value: string; }
  | { type: 'nodeLocator'; value: NullableNodeLocator; }
);
interface HighlightableMessage {
  start: number;
  end: number;
  msg: string;
}
type AsyncRpcUpdateValue = (
    { type: 'status'; value: string; }
  | { type: 'workerStackTrace'; value: string[]; }
  | { type: 'workerStatuses'; value: string[]; }
  | { type: 'workerTaskDone'; value: WorkerTaskDone; }
);
interface TALStep {
  type: string;
  label?: string;
  start: number;
  end: number;
  depth: number;
  external?: boolean;
}
interface Tracing {
  node: NodeLocator;
  prop: Property;
  dependencies: Tracing[];
  result: RpcBodyLine;
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
type NodeLocatorStep = (
    { type: 'child'; value: number; }
  | { type: 'nta'; value: FNStep; }
  | { type: 'tal'; value: TALStep; }
);
type ListedTreeChildNode = (
    { type: 'children'; value: ListedTreeNode[]; }
  | { type: 'placeholder'; value: number; }
);
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
interface BackingFile {
  path: string;
  value: string;
}
interface FNStep {
  property: Property;
}
type WorkerTaskDone = (
    { type: 'normal'; value: { [key: string]: any }; }
  | { type: 'unexpectedError'; value: string[]; }
);
interface NestedTest {
  path: number[];
  property: Property;
  expectedOutput: RpcBodyLine[];
  nestedProperties: NestedTest[];
}
interface NullableNodeLocator {
  type: string;
  value?: NodeLocator;
}
interface PropertyArgCollection {
  type: string;
  entries: PropertyArg[];
}


export { PutTestSuiteRes, TestSuiteOrError, PutTestSuiteReq, PollWorkerStatusReq, ParsingRequestData, HighlightableMessage, PollWorkerStatusRes, ListedTreeNode, TestSuite, TestSuiteListOrError, NestedTest, ListTreeRes, AsyncRpcUpdateValue, ListTreeReq, TopRequestResponseData, TALStep, GetTestSuiteRes, PropertyArgCollection, WsPutLongpollReq, HoverReq, WsPutLongpollRes, InitInfo, HoverRes, TunneledWsPutRequestReq, TunneledWsPutRequestRes, GetTestSuiteReq, RpcBodyLine, GetWorkerStatusRes, ListTestSuitesReq, GetWorkerStatusReq, Tracing, TopRequestRes, EvaluatePropertyReq, ListTestSuitesRes, SynchronousEvaluationResult, TopRequestReq, EvaluatePropertyRes, AsyncRpcUpdate, PropertyEvaluationResult, Diagnostic, WsPutInitRes, WsPutInitReq, Refresh, WorkerTaskDone, FNStep, LongPollResponse, SubscribeToWorkerStatusRes, BackingFile, SubscribeToWorkerStatusReq, SubmitWorkerTaskRes, StopJobRes, SubmitWorkerTaskReq, StopJobReq, Property, NullableNodeLocator, UnsubscribeFromWorkerStatusReq, CompleteRes, CompleteReq, UnsubscribeFromWorkerStatusRes, ListNodesReq, NodeLocator, ListNodesRes, PropertyArg, TestCase, ListedTreeChildNode, ListPropertiesReq, NodeLocatorStep, ListPropertiesRes }

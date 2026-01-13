import { assertUnreachable } from '../hacks';
import { GetWorkspaceFileReq, GetWorkspaceFileRes, ListWorkspaceDirectoryReq, ListWorkspaceDirectoryRes, PutWorkspaceContentReq, PutWorkspaceContentRes, RenameWorkspacePathReq, RenameWorkspacePathRes, UnlinkWorkspacePathReq, UnlinkWorkspacePathRes, WorkspaceEntry } from '../protocol';
import settings from '../settings';
import createModalTitle, { createOverflowButton } from '../ui/create/createModalTitle';
import showWindow from '../ui/create/showWindow';
import UIElements from '../ui/UIElements';
import TextProbeManager, { TextProbeCheckResults } from './TextProbeManager';
import ModalEnv from './ModalEnv';
import WindowState from './WindowState';
import ThreadPoolExecutor, { createThreadPoolExecutor } from './ThreadPoolExecutor';
import displayFuzzyWorkspaceFileFinder, { createDisplayFuzzyFinderShortcutListener } from '../ui/popup/displayFuzzyWorkspaceFileFinder';
import FileTreeManager, { Directory, DirectoryListEntry, setupFileTreeManager, TextFile } from './FileTreeManager';
import deepEqual from './util/deepEqual';

const uiElements = new UIElements();
const unsavedFileKey = '%😄'; // Hopefully not colliding with anything

interface WorkspaceInitArgs {
  env: ModalEnv;
  initialLocalContent: string;
  getLocalContent: () => string;
  setLocalContent: (contents: string, readOnly: boolean) => void;
  onActiveFileChanged: () => void;
  getCurrentWindows: () => WindowState[];
  setLocalWindows: (states: WindowState[]) => void;
  textProbeManager: TextProbeManager;
  notifySomeWorkspacePathChanged: () => void;
  serverSupportsWorkspaceMetadata: boolean;
}

interface VisibleRow {
  updateTestStatus: (status: TextProbeCheckResults) => void;
  click: () => void;
  clickIfClosedDir: () => void;
}

type CachedFileEntry = { contents: string; windows: WindowState[] };
type WorkspaceDirectory = Directory<CachedFileEntry>
type WorkspaceTextFile = TextFile<CachedFileEntry>

type CachedEntries<T> = { [path: string]: T };
interface Workspace {
  env: ModalEnv;
  visibleRows: CachedEntries<VisibleRow>;
  knownTestResults: CachedEntries<TextProbeCheckResults>;
  preOpenedFiles: { [path: string]: boolean };
  getActiveFile: () => string;
  activeFileIsTempFile: () => boolean;
  onActiveFileChange: (contents: string, states: WindowState[]) => void;
  onActiveFileChecked: (results: TextProbeCheckResults) => void;
  onServerNotifyPathsChanged: (paths: string[]) => void;
  setActiveWorkspacePath: (path: string) => void;
}

interface WorkspaceInstance extends Workspace {
  clientSideMetadataCache: { [path: string]: WindowState[] }
  treeManager: FileTreeManager<CachedFileEntry>;
  getMostRecentPutFileRequestContents: (path: string) => string | null,
}

interface WorkspaceFileMetadata {
  windowStates: WindowState[];
}

const displayTestModal = (args: WorkspaceInitArgs, workspace: WorkspaceInstance, extras: { shouldIgnoreChangeCallbacks: boolean }) => {
  const testBtn = uiElements.workspaceTestRunner;
  let numPass = 0;
  let numFail = 0;

  const testDir = async (
    statusLbl: HTMLElement,
    failureLog: HTMLElement,
    dir: WorkspaceDirectory,
    testRunMonitor: { shouldStop: boolean },
    executor: ThreadPoolExecutor,
  ) => {
    const contents = await dir.getChildren();
    if (!contents) {
      return;
    }
    for (let i = 0; i < contents.length; ++i) {
      const entry = contents[i];
      if (testRunMonitor.shouldStop) {
        return;
      }
      switch (entry.type) {
        case 'dir': {
          executor.submit(() => testDir(statusLbl, failureLog, entry.value, testRunMonitor, executor));
          break;
        }
        case 'file': {
          const checkFile = async () => {
            const txt = await entry.value.getContent();
            if (txt == null) {
              return;
            }
            if (testRunMonitor.shouldStop) {
              return;
            }
            const fullPath = entry.value.fullPath;
            const res = await args.textProbeManager.checkFile(
              { type: 'workspacePath', value: fullPath },
              txt.contents
            );
            if (res === null) {
              return;
            }
            numPass += res.numPass;
            numFail += res.numFail;
            const debug = location.search.includes('debug=true');
            if (res.numFail || debug) {
              const logEntry = document.createElement('div');
              logEntry.classList.add('workspace-test-failure-log-entry')
              logEntry.innerText = `${res.numFail} failure${res.numFail > 1 ? 's' : ''}${debug ? `, ${res.numPass} pass` : ''} in ${fullPath}`;

              failureLog.appendChild(logEntry);
              failureLog.style.display = 'flex';

              if (res.numFail) {
                logEntry.classList.add('clickHighlightOnHover');
                logEntry.onclick = () => workspace.setActiveWorkspacePath(fullPath);
              }
            }
            workspace.knownTestResults[fullPath] = res;
            statusLbl.innerText = `Running.. ${numPass} pass, ${numFail} fail`;

            const row = workspace.visibleRows[fullPath];
            if (row) {
              row.updateTestStatus(res);
            }
          };
          executor.submit(checkFile);
          break;
        }
        default: {
          assertUnreachable(entry);
          break;
        }
      }
    }
  };
  const queryId = `query-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;

  testBtn.disabled = true;
  testBtn.textContent = 'Running..';
  const cleanup = () => {
    testWindow.remove();
    delete args.env.onChangeListeners[queryId];
  };
  let currentlyRunning = false;
  let currentTestRunMonitor = { shouldStop: false };
  const rerun = () => {
    if (currentlyRunning) {
      currentTestRunMonitor.shouldStop = true;
      return;
    }
    currentlyRunning = true;
    currentTestRunMonitor.shouldStop = false;
    numFail = 0;
    numPass = 0;
    testWindow.refresh();
  }
  let shouldAutoRerun = settings.shouldRerunWorkspaceTestsOnChange();

  const testWindow = showWindow({
    rootStyle: `
      width: 32rem;
      min-height: 8rem;
    `,
    onForceClose: cleanup,
    render: (root, info) => {
      while (root.firstChild) {
        root.firstChild.remove();
      }
      root.appendChild(createModalTitle({
        renderLeft: (container) => {
          const header = document.createElement('span');
          header.innerText = 'Test Results';
          container.appendChild(header);
        },
        onClose: cleanup,
      }).element);

      const bodyWrapper = document.createElement('div');
      bodyWrapper.style.padding = '0.25rem';
      root.appendChild(bodyWrapper);

      const statusLbl = document.createElement('div');
      statusLbl.innerText = 'Running..';
      bodyWrapper.appendChild(statusLbl);
      const failureLog = document.createElement('div');
      failureLog.style.display = 'none';
      failureLog.classList.add('workspace-test-failure-log');
      bodyWrapper.appendChild(failureLog);

      const start = Date.now();
      const executor = createThreadPoolExecutor(Math.max(1, args.env.workerProcessCount ?? 1));
      testDir(statusLbl, failureLog, workspace.treeManager.root, currentTestRunMonitor, executor)
        .then(() => executor.wait())
        .catch((err) => {
          console.warn('Failed running tests', err)
        })
        .finally(() => {
          currentlyRunning = false;
          if (currentTestRunMonitor.shouldStop) {
            // We tried running again during the run, rerun!
            rerun();
            return;
          }
          if (info.cancelToken.cancelled) {
            return;
          }
          if (settings.getTextProbeStyle() === 'disabled') {
            const errLbl = document.createElement('div');
            errLbl.innerText = `Text probes are disabled, enable them in the settings panel and rerun.`;
            errLbl.classList.add('captured-stderr');
            bodyWrapper.appendChild(errLbl);
          }
          const doneLbl = document.createElement('div');
          doneLbl.innerText = `Done in ${Date.now() - start}ms`;
          bodyWrapper.appendChild(doneLbl);

          testBtn.disabled = false;
          testBtn.innerText = 'Run Tests'

          const repeatBtn = document.createElement('button');
          repeatBtn.innerText = 'Rerun';
          bodyWrapper.appendChild(repeatBtn);
          repeatBtn.onclick = rerun

          const autoRerun = document.createElement('input');
          autoRerun.type = 'checkbox';
          autoRerun.checked = shouldAutoRerun;
          const autoRerunId = `autorerun-id-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`
          autoRerun.id = autoRerunId;
          bodyWrapper.appendChild(autoRerun);

          const label = document.createElement('label');
          label.htmlFor = autoRerunId;
          label.innerText = 'Auto rerun on any file change'
          bodyWrapper.appendChild(label);
          autoRerun.onchange = () => {
            shouldAutoRerun = autoRerun.checked;
            settings.setShouldRerunWorkspaceTestsOnChange(shouldAutoRerun);
          };

        })
    },
  });
  args.env.onChangeListeners[queryId] = (_, reason) => {
    if (reason === 'refresh-from-server'
      || (shouldAutoRerun && !extras.shouldIgnoreChangeCallbacks)
    ) {
      rerun();
    }
  }
}

const createFileListManager = (
  workspace: WorkspaceInstance,
  dir: WorkspaceDirectory,
  path: string,
  setActive: (path: string, contents: CachedFileEntry, readOnly: boolean) => void,
  performedTypedRpc: ModalEnv['performTypedRpc'],
  fileList: HTMLElement,
): { refresh: () => void, } =>{
  const childRowsByName: { [name: string]: HTMLDivElement } = {};
  const refresh = () => {
    dir.getChildren().then(children => {
      if (!children) {
        console.warn('Failed listing children for', path, ', is the dir removed?');
        return;
      }
      const remainingChildren = new Set<string>(children.map(x => x.name));
      Object.entries(childRowsByName).forEach(([name, row]) => {
        if (!remainingChildren.has(name)) {
          row.remove();
          delete childRowsByName[name];
          if (workspace.getActiveFile().startsWith(`${path ? `${path}/` : ''}${name}`)) {
            // Active path disappeared!
            workspace.visibleRows[unsavedFileKey]?.click();
          }
        }
      });
      children.forEach((child, childIdx) => {
        if (!childRowsByName[child.name]) {
          childRowsByName[child.name] = fileList.appendChild(createRow(
            workspace,
            child.type === 'dir'
              ? { type: 'directory', value: child.value }
              : { type: 'file', value: child.value },
            child.name, path ? `${path}/${child.name}` : child.name,
            setActive,
            performedTypedRpc,
          ));
        }
        childRowsByName[child.name].style.order = `${childIdx}`;
      });
    });
  };
  dir.setChangeListener(refresh);

  fileList.appendChild(createAddFileButton(workspace, path, fileList, setActive)).style.order = '9999999';

  return {
    refresh
  };
}

type RowKind =
  { type: 'unsaved' }
  | { type: 'file', value: WorkspaceTextFile }
  | { type: 'directory', value: WorkspaceDirectory }
const createRow = (
  workspace: WorkspaceInstance,
  kind: RowKind,
  label: string,
  path: string,
  setActive: (path: string, contents: CachedFileEntry, readOnly: boolean) => void,
  performedTypedRpc: ModalEnv['performTypedRpc'],
): HTMLDivElement => {
  const row = document.createElement('div');
  row.classList.add('workspace-row');
  if (path === workspace.getActiveFile()) {
    row.classList.add('workspace-row-active');
  }

  let classKind: 'unsaved' | 'dir-open' | 'dir-closed' | 'file' = 'file';
  const changeKind = (newKind: typeof classKind) => {
    row.classList.remove(`workspace-${classKind}`);
    classKind = newKind;
    row.classList.add(`workspace-${classKind}`);
  }

  const headerLine = document.createElement('div');
  headerLine.classList.add('workspace-row-header');
  row.appendChild(headerLine);

  const lbl = document.createElement('span');
  lbl.classList.add('clickHighlightOnHover');
  lbl.innerText = label;
  headerLine.appendChild(lbl);

  if (kind.type !== 'unsaved') {
    const btn = createOverflowButton([
      {
        title: 'Rename',
        invoke: () => {
          const newPath = prompt(`Enter new name for ${path}`, path);
          if (!newPath || newPath === path) {
            return;
          }
          performedTypedRpc<RenameWorkspacePathReq, RenameWorkspacePathRes>({ type: 'RenameWorkspacePath', srcPath: path, dstPath: newPath })
            .then((res) => {
              if (res.ok) {
                const oldParentDir = path.includes('/') ? path.slice(0, path.lastIndexOf('/')) : '';
                const newParentDir = newPath.includes('/') ? newPath.slice(0, newPath.lastIndexOf('/')) : '';
                workspace.treeManager.notifyChanges([oldParentDir, newParentDir]);
                if (kind.type === 'file' && path === workspace.getActiveFile()) {
                  const content = kind.value.getCachedContent();
                  if (content) {
                    setActive(newPath, content, false);
                  }
                }
                return;
              }
              throw new Error(`Got non-ok response`);
            })
            .catch((err) => {
              console.warn('Failed renaming', path, 'to', newPath, ':', err);
            })

        },
      },
      {
        title: 'Delete',
        invoke: () => {
          const sure = confirm('Are you sure? This cannot be undone');
          if (!sure) {
            return;
          }
          performedTypedRpc<UnlinkWorkspacePathReq, UnlinkWorkspacePathRes>({ type: 'UnlinkWorkspacePath', path })
            .then((res) => {
              if (res.ok) {
                const parentDir = path.includes('/') ? path.slice(0, path.lastIndexOf('/')) : '';
                workspace.treeManager.notifyChanges([parentDir]);
                return;
              }
              throw new Error(`Got non-ok response`);
            })
            .catch(err => {
              console.warn('Failed removing', path, ':', err);
            });
        },
      },
    ]);
    btn.classList.add('workspace-row-overflow');
    headerLine.appendChild(btn);
  }

  row.setAttribute('data-path', path);
  const updateTestStatus: VisibleRow['updateTestStatus'] = (status) => {
    row.classList.remove('workspace-row-test-success');
    row.classList.remove('workspace-row-test-fail');
    if (status.numFail) {
      row.classList.add('workspace-row-test-fail');
    } else if (status.numPass) {
      row.classList.add('workspace-row-test-success');
    } else {
      // No tests in the file, don't decorate it with any color
    }
  };
  if (workspace.knownTestResults[path]) {
    updateTestStatus(workspace.knownTestResults[path]);
  }
  let click: () => void;
  let clickIfClosedDir: () => void = () => {};
  const activateTempFile = () => setActive(path, { contents: settings.getEditorContents() ?? '', windows: settings.getProbeWindowStates() }, false);
  switch (kind.type) {
    case 'unsaved':
      click = activateTempFile;
      changeKind('unsaved');
      break;

    case 'file':
      const tfile = kind.value;
      changeKind('file');
      click = () => {
        tfile.getContent()
          .then(text => {
            if (text !== null) {
              setActive(path, text, tfile.readOnly());
            }
          });
      }
      tfile.setChangeListener(() => {
        if (path !== workspace.getActiveFile()) {
          // We changed, but are not currently active. Ignore
          return;
        }
        if (tfile.isRemoved()) {
          // We were the active file, but we were removed! Change back to the temp file
          activateTempFile();
          return;
        }
        let preContents = workspace.getMostRecentPutFileRequestContents(path);
        tfile.getContent()
          .then(text => {
            if (text === null) {
              console.warn('Failed fetching file after update, is it removed? Path:', path);
              return;
            }
            const posContents = workspace.getMostRecentPutFileRequestContents(path);
            if (
                  path === workspace.getActiveFile() // We are still the active file
              && preContents === posContents // The user didn't type any new character since we started loading
            ) {
              setActive(path, text, tfile.readOnly());
            }
          });
      })
      if (path === workspace.getActiveFile()) {
        click();
      }
      break;

      case 'directory': {
        const fileList = row.appendChild(document.createElement('div'));
        fileList.classList.add('workspace-dir-filelist');
        if (path.split('/').length % 2 === 1) {
          fileList.classList.add('workspace-dir-filelist-odd');
        }
        fileList.style.display = 'none';
        let dirState: 'open' | 'closed' = 'closed';
        const dir = kind.value;
        changeKind(`dir-${dirState}`);
        const listManager = createFileListManager(workspace, dir, path, setActive, performedTypedRpc, fileList);

        click = () => {
          dirState = dirState === 'open' ? 'closed' : 'open';
          changeKind(`dir-${dirState}`);
          switch (dirState) {
            case 'closed': {
              fileList.style.display = 'none';
              break;
            }
            case 'open': {
              fileList.style.display = 'flex';
              listManager.refresh();
              break;
            }
            default: {
              assertUnreachable(dirState);
              break;
            }
          }
        };
        if (workspace.preOpenedFiles[path]) {
          click();
        }
        clickIfClosedDir = () => {
          if (dirState === 'closed') {
            click();
          }
        };
        break;
      }
      default:
        assertUnreachable(kind);
        return row;
  }

  workspace.visibleRows[path] = {
    updateTestStatus,
    click,
    clickIfClosedDir,
  };
  lbl.onclick = click;
  return row;
}

const createAddFileButton = (workspace: WorkspaceInstance, basePath: string, tgtContainer: HTMLElement, setActive: (path: string, contents: CachedFileEntry, readOnly: boolean) => void) => {
  const row = document.createElement('div');
  row.classList.add('workspace-row');
  row.classList.add(`workspace-addfile`);

  const headerLine = document.createElement('div');
  headerLine.classList.add('workspace-row-header');
  row.appendChild(headerLine);

  const lbl = document.createElement('span');
  lbl.classList.add('clickHighlightOnHover');
  lbl.innerText = '+';
  headerLine.appendChild(lbl);

  row.onclick = () => {
    const name = prompt(`Name the new file${basePath ? ` in ${basePath}` : ''}`);
    if (name) {
      const fullPath = `${basePath ? `${basePath}/` : ''}${name}`;

      // First, check if the file exists
      (async () => {
        let fileAlreadyExists = !!(await workspace.treeManager.lookup(fullPath));
        if (fileAlreadyExists) {
          alert('File already exists');
          return;
        }
        const putRes = await workspace.env.performTypedRpc<PutWorkspaceContentReq, PutWorkspaceContentRes>({ type: 'PutWorkspaceContent', path: fullPath, content: '' });
        if (putRes.ok) {
          // OK!
          workspace.treeManager.notifyChanges([basePath]);
          setActive(fullPath, { contents: '', windows: [] }, false); // Expect readOnly=false in new files
        } else {
          alert('Failed creating the file, check the server log for details');
        }
      })();
    }
  };

  return row;
}

const initWorkspace = async (args: WorkspaceInitArgs): Promise<Workspace | null> => {
  let activeFile = unsavedFileKey;
  const workspaceList = uiElements.workspaceListWrapper;
  let setActiveFile = (path: string, data: CachedFileEntry, readOnly: boolean) => { }
  const mostRecentPutFileRequestContents: { [path: string]: string } = {};
  const ignoreWindowUpdatesForPath: { [path: string]: boolean } = {};
  const treeManager = setupFileTreeManager<CachedFileEntry>({
    getFileContent: async path => {
      const fresh = await workspace.env.performTypedRpc<GetWorkspaceFileReq, GetWorkspaceFileRes>({ type: 'GetWorkspaceFile', path, loadMeta: !ignoreWindowUpdatesForPath[path] });
      if (typeof fresh?.content !== 'string') {
        console.log('Failed GetWorkspaceFile for path:"', path, '"');
        return null;
      }
      let ret: CachedFileEntry = { contents: fresh.content, windows: [] };
      if (fresh.metadata) {
        ret.windows = (fresh.metadata as WorkspaceFileMetadata).windowStates ?? [];
      } else if (workspace.clientSideMetadataCache[path]) {
        ret.windows = workspace.clientSideMetadataCache[path];
      }
      return ret;
    },
    listDirectory: async path => {
      const fresh = await workspace.env.performTypedRpc<ListWorkspaceDirectoryReq, ListWorkspaceDirectoryRes>({ type: 'ListWorkspaceDirectory', path });
      if (!fresh?.entries) {
        console.log('Failed ListWorkspaceDirectory for path:"', path, '"');
        return null;
      }
      const extractEntryName = (v: DirectoryListEntry) => v.type === 'directory' ? v.value : v.value.name;
      fresh.entries.sort((a, b) => {
        if (a.type !== b.type) {
          return a.type === 'directory' ? -1 : 1;
        }
        return extractEntryName(a).localeCompare(extractEntryName(b));
      })
      return fresh.entries;
    },
  })
  const workspace: WorkspaceInstance = {
    env: args.env,
    preOpenedFiles: {},
    visibleRows: {},
    knownTestResults: {},
    treeManager,
    getActiveFile: () => activeFile,
    activeFileIsTempFile: () => activeFile === unsavedFileKey,
    onActiveFileChange: (contents, newStates) => {
      const path = activeFile;
      if (path === unsavedFileKey) {
        // Ignore
        return;
      }
      // Make copy so we don't hold live pointers to data that may change
      // We want to be able to compare later on to check for updates, live data prohibits this.
      const stateCopy = JSON.parse(JSON.stringify(newStates));
      let states: WindowState[] | null = stateCopy;

      workspace.clientSideMetadataCache[path] = stateCopy;
      mostRecentPutFileRequestContents[path] = contents;
      const cached = treeManager.lookupCached(activeFile);
      if (cached?.type !== 'file') {
        // First change in new file perhaps. Should only happen if they start typing
        // immediately after creating a file (before the directory refresh request finishes).
        // No caching to check
      } else {
        if (cached.value.readOnly()) {
          states = null;
        }
        const ccontent = cached.value.getCachedContent();
        const sameContent = ccontent?.contents === contents;
        // Read-only window states, or same windows?
        const sameWindowsIsh = !states || deepEqual(ccontent?.windows ?? [], states, { ignoreUndefinedDiff: true });
        cached.value.setCachedContent({ contents, windows: stateCopy });
        if (sameContent && sameWindowsIsh) {
          // Ignore it
          return;
        }
      }
      args.env.putWorkspaceContent(path, contents, states);
    },
    getMostRecentPutFileRequestContents: (path: string) => mostRecentPutFileRequestContents[path] ?? null,
    onActiveFileChecked: (res) => {
      workspace.knownTestResults[activeFile] = res;
      if (workspace.visibleRows[activeFile]) {
        workspace.visibleRows[activeFile].updateTestStatus(res);
      }
    },
    onServerNotifyPathsChanged: (paths) => treeManager.notifyChanges(paths),
    setActiveWorkspacePath: (path) => {
      if (workspace.visibleRows[path]) {
        workspace.visibleRows[path].click();
        return;
      }

      // Else, path not currently visible. Need to mark all dirs in the path as pre-opened, and then
      // click the top unopened dir. Unopened sub-dirs will then automatically open afterwards.
      const parts = path.split('/');
      parts.forEach((_segment, idx) => {
        const subPath = parts.slice(0, idx + 1).join('/');
        if (idx < parts.length - 1) {
          workspace.preOpenedFiles[subPath] = true;
          workspace.visibleRows[subPath]?.clickIfClosedDir();
        }
      });
    },
    clientSideMetadataCache: {},
  };

  {
    const fromSettings = settings.getActiveWorkspacePath();
    if (fromSettings !== null && fromSettings !== unsavedFileKey) {
      if ((await treeManager.lookup(fromSettings))?.type === 'file') {
        activeFile = fromSettings;
        const parts = fromSettings.split('/');
        for (let i = 0; i < parts.length - 1; ++i) {
          workspace.preOpenedFiles[parts.slice(0, i + 1).join('/')] = true;
        }
      }
    }
  }
  const testModalExtras = { shouldIgnoreChangeCallbacks: false };
  uiElements.workspaceTestRunner.onclick = () => displayTestModal(args, workspace, testModalExtras);
  uiElements.workspaceFindFile.onclick = () => displayFuzzyWorkspaceFileFinder(args.env, workspace, true);
  document.addEventListener(
    'keydown',
    createDisplayFuzzyFinderShortcutListener(() => displayFuzzyWorkspaceFileFinder(args.env, workspace, false)),
    true,
  );

  const performSetup = async (): Promise<Workspace | null> => {
    const rootRes = await treeManager.root.getChildren();
    if (rootRes === null) {
      // We expect empty array if there is an empty workspace.
      // No array at all means there is no workspace to work with
      return null;
    }
    document.body.classList.add('workspace-visible');
    uiElements.workspaceHeaderLabel.style.display = 'flex';
    workspaceList.style.display = 'flex';

    const setActiveStyling = (activePath: string) => {
      document.querySelectorAll('.workspace-row').forEach(elem => {
        if (elem.getAttribute('data-path') === activePath) {
          elem.classList.add('workspace-row-active')
        } else {
          elem.classList.remove('workspace-row-active')
        }
      });
    }
    setActiveFile = (path: string, data: CachedFileEntry, readOnly: boolean) => {
      const isUpdateWithinCurrentFile = activeFile === path;
      activeFile = path;
      workspace.clientSideMetadataCache[path] = data.windows;
      testModalExtras.shouldIgnoreChangeCallbacks = true;
      const currentWindows = args.getCurrentWindows();
      if (ignoreWindowUpdatesForPath[path]) {
        // Ignore
      } else if (isUpdateWithinCurrentFile && deepEqual(currentWindows, data.windows, { ignoreUndefinedDiff: true })) {
        // Ignore window update, no change
      } else {
        document.querySelectorAll('.auto-click-on-workspace-switch').forEach(btn => {
          if ((btn as any).customWorkspaceSwitchHandler) {
            (btn as any).customWorkspaceSwitchHandler();
          } else {
            (btn as HTMLElement).click();
          }
        });
        setTimeout(() => args.setLocalWindows(data.windows), 1);
        // Ignore future updates for windows in this file. We don't expect the windows to be updated outside CodeProber anyway.
        // IF two people connect to the same CodeProber instance then the windows can be externally modified.
        // However, that seems unlikely. Also, they can just reload to see eachothers updated windows.
        // Ignoring window data saves some network traffic (thanks to `loadMeta` being false above).
        ignoreWindowUpdatesForPath[path] = true;
      }
      if (isUpdateWithinCurrentFile && data.contents == args.getLocalContent()) {
        // Ignore content update, no change
      } else {
        args.setLocalContent(data.contents, readOnly);
      }
      setActiveStyling(path);
      args.onActiveFileChanged();
      settings.setActiveWorkspacePath(path);
      testModalExtras.shouldIgnoreChangeCallbacks = false;
    }
    workspaceList.appendChild(createRow(workspace, { type: 'unsaved' }, 'Temp file (browser only)', unsavedFileKey, setActiveFile, workspace.env.performTypedRpc));

    const rootFileList = workspaceList.appendChild(document.createElement('div'));
    rootFileList.style.display = 'flex';
    rootFileList.style.flexDirection = 'column';
    const rootFileListManager = createFileListManager(workspace, treeManager.root, '', setActiveFile, workspace.env.performTypedRpc, rootFileList);
    rootFileListManager.refresh();

    setActiveStyling(unsavedFileKey);
    return workspace;
  }

  return performSetup();
}


export { initWorkspace };
export default Workspace;

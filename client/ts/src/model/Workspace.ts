import { assertUnreachable } from '../hacks';
import { GetWorkspaceFileReq, GetWorkspaceFileRes, ListWorkspaceDirectoryReq, ListWorkspaceDirectoryRes, PutWorkspaceContentReq, PutWorkspaceContentRes, PutWorkspaceMetadataReq, PutWorkspaceMetadataRes, WorkspaceEntry } from '../protocol';
import settings from '../settings';
import createModalTitle, { createOverflowButton } from '../ui/create/createModalTitle';
import showWindow from '../ui/create/showWindow';
import UIElements from '../ui/UIElements';
import TextProbeManager, { TextProbeCheckResults } from './TextProbeManager';
import ModalEnv from './ModalEnv';
import WindowState from './WindowState';

const uiElements = new UIElements();
const unsavedFileKey = '%😄'; // Hopefully not colliding with anything

interface WorkspaceInitArgs {
  env: ModalEnv;
  initialLocalContent: string;
  setLocalContent: (contents: string) => void;
  onActiveFileChanged: () => void;
  getCurrentWindows: () => WindowState[];
  setLocalWindows: (states: WindowState[]) => void;
  textProbeManager: TextProbeManager;
  notifySomeWorkspacePathChanged: () => void;
}

interface VisibleRow {
  updateTestStatus: (status: TextProbeCheckResults) => void;
}

type CachedFileEntry = { contents: string; windows: WindowState[] };
type CachedDirEntry = { files: WorkspaceEntry[] };
type CachedEntries<T> = { [path: string]: T };
type ReloadReason =
    { type: 'rename', src: string, dst: string }
  | { type: 'unlink', path: string }
  ;
interface Workspace {
  env: ModalEnv;
  cachedFiles: CachedEntries<CachedFileEntry>;
  cachedDirs: CachedEntries<CachedDirEntry>;
  visibleRows: CachedEntries<VisibleRow>;
  knownTestResults: CachedEntries<TextProbeCheckResults>;
  preOpenedFiles: { [path: string]: boolean };
  getActiveFile: () => string;
  activeFileIsTempFile: () => boolean;
  onActiveFileChange: (contents: string) => void;
  onActiveWindowsChange: (states: WindowState[]) => void;
  reload: (reason?: ReloadReason) => void;
  onActiveFileChecked: (results: TextProbeCheckResults) => void;
  onServerNotifyPathsChanged: (paths: string[]) => void;
}


interface WorkspaceFileMetadata {
  windowStates: WindowState[];
}


const displayTestModal = (args: WorkspaceInitArgs, workspace: Workspace, extras: { shouldIgnoreChangeCallbacks: boolean }) => {
  const testBtn = uiElements.workspaceTestRunner;
  let numPass = 0;
  let numFail = 0;

  const testDir = async (statusLbl: HTMLElement, failureLog: HTMLElement, path: string | null = null, testRunMonitor: { shouldStop: boolean}) => {
    const contents = await getDirContents(workspace, path ?? '');
    if (!contents) {
      return;
    }
    for (let i = 0; i < contents.files.length; ++i) {
      const entry = contents.files[i];
      const fullPath = `${path ? `${path}/` : ''}${entry.value}`;
      if (testRunMonitor.shouldStop) {
        return;
      }
      switch (entry.type) {
        case 'directory': {
          await testDir(statusLbl, failureLog, fullPath, testRunMonitor);
          break;
        }
        case 'file': {
          const entry = await getFileContents(workspace, fullPath);
          if (entry == null) {
            continue;
          }
          if (testRunMonitor.shouldStop) {
            return;
          }
          const res = await args.textProbeManager.checkFile(
            // { type: 'text', value: entry.contents },
            { type: 'workspacePath', value: fullPath },
            entry.contents
          );
          if (res === null) {
            continue;
          }

          numPass += res.numPass;
          numFail += res.numFail;
          if (res.numFail) {
            const logEntry = document.createElement('div');
            logEntry.classList.add('workspace-test-failure-log-entry')
            logEntry.innerText = `${res.numFail} failure${res.numFail > 1 ? 's' : ''} in ${fullPath}`;

            failureLog.appendChild(logEntry);
            failureLog.style.display = 'flex';
          }
          workspace.knownTestResults[fullPath] = res;
          statusLbl.innerText = `Running.. ${numPass} pass, ${numFail} fail`;

          const row = workspace.visibleRows[fullPath];
          if (row) {
            row.updateTestStatus(res);
          }
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
  console.log('displayTestModal')
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
      testDir(statusLbl, failureLog, null, currentTestRunMonitor)
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
        const doneLbl = document.createElement('div');
        doneLbl.innerText =`Done in ${Date.now() - start}ms`;
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

async function getFileContents(workspace: Workspace, path: string): Promise<CachedFileEntry | null> {
  const cached = workspace.cachedFiles[path];
  if (cached) {
    return cached;
  }
  const fresh = await workspace.env.performTypedRpc<GetWorkspaceFileReq, GetWorkspaceFileRes>({ type: 'GetWorkspaceFile', path });
  if (typeof fresh?.content !== 'string') {
    console.log('bad resp for gFC', path);
    return null;
  }
  if (!workspace.cachedFiles[path]) {
    workspace.cachedFiles[path] = { contents: fresh.content, windows: [] };
  } else {
    workspace.cachedFiles[path].contents = fresh.content;
  }
  if (fresh.metadata) {
    try {
      workspace.cachedFiles[path].windows = (fresh.metadata as WorkspaceFileMetadata).windowStates;
    } catch (e) {
      console.warn('Failed parsing metadata', e);
      console.warn('Metadata: ', fresh.metadata);
    }
  }
  return workspace.cachedFiles[path];
}
async function getDirContents(workspace: Workspace, path: string): Promise<CachedDirEntry | null> {
  const cached = workspace.cachedDirs[path];
  if (cached) {
    return cached;
  }
  const fresh = await workspace.env.performTypedRpc<ListWorkspaceDirectoryReq, ListWorkspaceDirectoryRes>({ type: 'ListWorkspaceDirectory', path: path || undefined });
  if (!fresh?.entries) {
    return null;
  }
  if (!workspace.cachedDirs[path]) {
    workspace.cachedDirs[path] = { files: fresh.entries };
  } else {
    workspace.cachedDirs[path].files = fresh.entries;
  }
  return workspace.cachedDirs[path];
}

type RowKind = 'unsaved' | 'file' | 'dir-open' | 'dir-closed';
const createRow = (
  workspace: Workspace,
  kind: RowKind,
  label: string,
  path: string,
  setActive: (path: string, contents: CachedFileEntry) => void,
) => {
  const row = document.createElement('div');
  row.classList.add('workspace-row');
  row.classList.add(`workspace-${kind}`);
  if (path === workspace.getActiveFile()) {
    row.classList.add('workspace-row-active');
  }

  const changeKind = (newKind: typeof kind) => {
    console.log('kind', kind, '-->', newKind);
    row.classList.remove(`workspace-${kind}`);
    kind = newKind;
    row.classList.add(`workspace-${kind}`);
  }

  const headerLine = document.createElement('div');
  headerLine.classList.add('workspace-row-header');
  row.appendChild(headerLine);

  const lbl = document.createElement('span');
  lbl.classList.add('clickHighlightOnHover');
  lbl.innerText = label;
  headerLine.appendChild(lbl);

  if (kind !== 'unsaved') {
    const btn = createOverflowButton([
      {
        title: 'Rename',
        invoke: () => {
          const newPath = prompt(`Enter new name for ${path}`, path);
          if (!newPath || newPath === path) {
            return;
          }
          // TODO refactor remaining fetch'es to performedTypedRpc
          fetch(`api/workspace/rename?src=${encodeURIComponent(path)}&dst=${encodeURIComponent(newPath)}`, { method: 'PUT' })
            .then(res => {
              if (res.status === 200) {
                workspace.reload({ type: 'rename', src: path, dst: newPath });
                return;
              }
              throw new Error(`Unexpected status: ${res.status}`);
            })
            .catch(err => {
              console.warn('Failed renaming', path, 'to', newPath);
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
          fetch(`api/workspace/unlink?f=${encodeURIComponent(path)}`, { method: 'PUT' })
            .then(res => {
              if (res.status === 200) {
                workspace.reload({ type: 'unlink', path });
                return;
              }
              throw new Error(`Unexpected status: ${res.status}`);
            })
            .catch(err => {
              console.warn('Failed removing', path);
            })
        },
      },
    ]);
    btn.classList.add('workspace-row-overflow');
    headerLine.appendChild(btn);
  }

  let fileList: HTMLDivElement | null = null;
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
  workspace.visibleRows[path] = {
    updateTestStatus,
  };
  const click = () => {
    switch (kind) {
      case 'unsaved':
      case 'file':
        getFileContents(workspace, path)
          .then(text => {
            if (text !== null) {
              setActive(path, text);
            }
          });
        break;

      case 'dir-closed':
        changeKind('dir-open');
        workspace.preOpenedFiles[path] = true;
        if (fileList) {
          fileList.style.display = 'flex';
        } else {
          fileList = document.createElement('div');
          fileList.classList.add('workspace-dir-filelist');
          if (path.split('/').length % 2 === 1) {
            fileList.classList.add('workspace-dir-filelist-odd');
          }
          getDirContents(workspace, path)
            .then(contents => {
              console.log('contents for', path, '==>', contents);
              if (contents !== null) {
                contents.files.forEach(file => {
                  fileList?.appendChild(createRow(
                    workspace,
                    file.type === 'directory' ? 'dir-closed' : 'file',
                    file.value,
                    `${path}/${file.value}`,
                    setActive,
                  ));
                })
              }
            })
            .finally(() => {
              fileList?.appendChild(createAddFileButton(workspace, `${path}/`, fileList, setActive))
              row.appendChild(fileList as HTMLElement);
            });
        }

        break;

      case 'dir-open':
        if (!fileList) {
          console.error('How can there be an open dir without a fileList??')
          return;
        }
        fileList.style.display = 'none';
        changeKind('dir-closed');
        workspace.preOpenedFiles[path] = false;
        break;

      default:
        assertUnreachable(kind);
    }
  };
  lbl.onclick = click;
  if ((kind === 'dir-closed' && workspace.preOpenedFiles[path])
      || (kind === 'file' && path === workspace.getActiveFile())) {
    click();
  }
  return row;
}

const createAddFileButton = (workspace: Workspace, basePath: string, tgtContainer: HTMLElement, setActive: (path: string, contents: CachedFileEntry) => void) => {
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
      const subPath = `${basePath}${name}`
      if (name.includes('/')) {
        // A little lazy, but lets just rename everything
        console.log('putting empty file...')
        fetch(
          `api/workspace/contents?f=${encodeURIComponent(subPath)}`,
          { method: 'PUT', body: '' }
        )
          .then(() => {
            console.log('then...');
          })
          .finally(() => {
            console.log('reload...pls');
            workspace.reload({ type: 'rename', src: workspace.getActiveFile(), dst: subPath })
          })
      } else {
        tgtContainer.appendChild(createRow(workspace, 'file', name, subPath, setActive));
        setActive(subPath, { contents: '', windows: [] });
      }
    }
  };

  return row;
}


const initWorkspace = async (args: WorkspaceInitArgs): Promise<Workspace | null> => {
  let activeFile = unsavedFileKey;
  const workspaceList = uiElements.workspaceListWrapper;
  let setActiveFile = (path: string, data: CachedFileEntry) => {}
  const workspace: Workspace = {
    env: args.env,
    cachedFiles: {},
    cachedDirs: {},
    preOpenedFiles: {},
    visibleRows: {},
    knownTestResults: {},
    getActiveFile: () => activeFile,
    activeFileIsTempFile: () => activeFile === unsavedFileKey,
    onActiveFileChange: (contents) => {
      if (workspace.cachedFiles[activeFile]) {
        if (workspace.cachedFiles[activeFile].contents == contents) {
          // Ignore it
          return;
        }
        workspace.cachedFiles[activeFile].contents = contents;
      }
      if (activeFile !== unsavedFileKey) {
        const path = activeFile;
        args.env.performTypedRpc<PutWorkspaceContentReq, PutWorkspaceContentRes>({ type: 'PutWorkspaceContent', path, content: contents })
          .then((res) => {
            if (!res.ok) {
              console.warn('Failed updating content for', path);
            }
          })
      }
    },
    onActiveWindowsChange: (states) => {
      if (workspace.cachedFiles[activeFile]) {
        workspace.cachedFiles[activeFile].windows = states;
      }
      if (activeFile !== unsavedFileKey) {
        const payload: WorkspaceFileMetadata = {
          windowStates: states,
        };
        const path = activeFile;
        args.env.performTypedRpc<PutWorkspaceMetadataReq, PutWorkspaceMetadataRes>({ type: 'PutWorkspaceMetadata', path, metadata: payload })
          .then((res) => {
            if (!res.ok) {
              console.warn('Failed updating metadata for', path);
            }
          })
          .catch(console.error);
      }
    },
    reload: (reason) => {
      const preActive = activeFile;
      Object.keys(workspace.cachedFiles).forEach(key => {
        if (key !== unsavedFileKey) { delete workspace.cachedFiles[key] }
      });
      Object.keys(workspace.cachedDirs).forEach(key => delete workspace.cachedDirs[key]);

      let newActiveFileName = preActive;
      if (reason) {
        switch (reason.type) {
          case 'rename': {
            if (preActive.startsWith(reason.src)) {
              newActiveFileName = `${reason.dst}${preActive.slice(reason.src.length)}`;
            }
            break;
          }
          case 'unlink': {
            if (preActive.startsWith(reason.path)) {
              newActiveFileName = unsavedFileKey;
            }
            break;
          }
        }
      }
      while (workspaceList.firstChild) {
        workspaceList.firstChild.remove();
      }
      performSetup()
        .then((ws) => {
          if (ws == null) {
            throw new Error('No workspace active after reload');
          }
          return getFileContents(workspace, newActiveFileName);
        })
        .then(contents => {
          if (contents) {
            setActiveFile(newActiveFileName, contents);
          } else {
            throw new Error(`Failed getting ${newActiveFileName} contents after reload`);
          }
        })
        .catch((err) => {
          console.error('Failed reloading workspace', err);
        })
    },
    onActiveFileChecked: (res) => {
      workspace.knownTestResults[activeFile] = res;
      if (workspace.visibleRows[activeFile]) {
        workspace.visibleRows[activeFile].updateTestStatus(res);
      }
    },
    onServerNotifyPathsChanged: async (paths) => {
      // Poll all changed paths
      let anyChange = false;
      let activeFileAtTimeofNewContent = activeFile;
      let newLocalFileContent: string | null = null;
      for (let i = 0; i < paths.length; ++i) {
        const path = paths[i];
        if (workspace.cachedFiles[path]) {
          const prevContent = workspace.cachedFiles[path];
          delete workspace.cachedFiles[path];
          const newContent = await getFileContents(workspace, path);
          if (!newContent) {
            continue;
          }
          if (prevContent.contents !== newContent.contents) {
            anyChange = true;
            if (path === activeFile) {
              activeFileAtTimeofNewContent = activeFile;
              newLocalFileContent = newContent.contents;
            }
          }
        }
      }
      if (newLocalFileContent !== null && activeFile === activeFileAtTimeofNewContent) {
        // This will naturally trigger a "onChange"
        args.env.setLocalState(newLocalFileContent);
      } else if (anyChange) {
        // Manually notify of a change, for example to get tests to re-run
        args.notifySomeWorkspacePathChanged();
      }
    }
  };

  {
    const fromSettings = settings.getActiveWorkspacePath();
    if (fromSettings !== null) {
      if ((await getFileContents(workspace, fromSettings)) !== null) {
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

  workspace.cachedFiles[unsavedFileKey] = {
    contents: args.initialLocalContent,
    windows: settings.getProbeWindowStates(),
  };
  const performSetup = async (): Promise<Workspace | null> => {
    const initialListingRes = await args.env.performTypedRpc<ListWorkspaceDirectoryReq, ListWorkspaceDirectoryRes>({ type: 'ListWorkspaceDirectory', });
    if (!initialListingRes.entries) {
      // We expect empty array if there is a workspace, but it is empty
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
    setActiveFile = (path: string, data: CachedFileEntry) => {
      activeFile = path;
      document.querySelectorAll('.auto-click-on-workspace-switch').forEach(btn => {
        (btn as HTMLButtonElement).click();
      })
      testModalExtras.shouldIgnoreChangeCallbacks = true;
      args.setLocalContent(data.contents);
      args.setLocalWindows(data.windows)
      setActiveStyling(path);
      args.onActiveFileChanged();
      settings.setActiveWorkspacePath(path);
      testModalExtras.shouldIgnoreChangeCallbacks = false;
    }
    workspaceList.appendChild(createRow(workspace, 'unsaved', 'Temp file (browser only)', unsavedFileKey, setActiveFile));
    initialListingRes.entries.forEach((file) => {
      workspaceList.appendChild(createRow(
        workspace,
        file.type === 'directory' ? 'dir-closed' : 'file',
        file.value,
        file.value,
        setActiveFile,
      ));
    });
    workspaceList.appendChild(createAddFileButton(workspace, '', workspaceList, setActiveFile));

    setActiveStyling(unsavedFileKey);
    return workspace;
  }

  return performSetup();
}


export { initWorkspace };
export default Workspace;

import ModalEnv from '../../model/ModalEnv';
import Workspace from '../../model/Workspace';
import { FindWorkspaceFilesReq, FindWorkspaceFilesRes } from '../../protocol';

let lastTypedQuery = '';
const displayFuzzyWorkspaceFileFinder = (env: ModalEnv, workspace: Workspace, showShortcutHint: boolean) => {
  // Helper function to make element creation nicer
  const add: <K  extends keyof HTMLElementTagNameMap>(parent: HTMLElement, kind: K) => HTMLElementTagNameMap[K] = (parent, kind) => parent.appendChild(document.createElement(kind));

  const root = add(document.body, 'div');
  root.classList.add('fuzzyFileFinder');
  root.classList.add('modalWindowColoring');

  const input = add(root, 'input')
  input.type = 'text';
  input.placeholder = `Search files by name`
  input.value = lastTypedQuery;

  const resultArea = add(root, 'div');
  resultArea.tabIndex = -1;
  resultArea.classList.add('resultArea');

  const resultStatus = add(resultArea, 'p');

  let currentSearchId = 0;
  let requestInProgress = false;
  let nextRequestQuery: string | null = null;
  const rowList: HTMLDivElement[] = [];

  input.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      if (rowList.length === 1) {
        rowList[0].click();
      }
    } else if (e.key === 'ArrowDown' && rowList.length) {
      e.preventDefault();
      rowList[0].focus();
    }
  })
  const cleanupResultArea = () => {
    while (resultArea.childElementCount > 1) {
      resultArea.lastChild?.remove();
    }
    rowList.length = 0;
  }
  const updateResultArea = (query = input.value) => {
    if (requestInProgress) {
      nextRequestQuery = query;
      return;
    }
    lastTypedQuery = query;
    if (!query) {
      cleanupResultArea();
      resultStatus.innerText = `No search filter applied${!showShortcutHint ? `` : `.\nTip: you can open this panel using Cmd+P or Ctrl+P`}`
      return;
    }
    // No need to show the hint again if the user types something and later clears the field.
    showShortcutHint = false;
    requestInProgress = true;
    resultStatus.innerText = `Loading..`

    const id = ++currentSearchId;
    (async () => {
      const res = await env.performTypedRpc<FindWorkspaceFilesReq, FindWorkspaceFilesRes>({
        type: 'FindWorkspaceFiles',
        query: query,
      })
      if (currentSearchId !== id) {
        // Another search started, ignore our result
        return;
      }
      cleanupResultArea();
      if (!res.matches?.length) {
        resultStatus.innerText = `No matches`;
        return;
      }
      const matches = res.matches;
      if (matches.length === 1) {
        resultStatus.innerText = `Found 1 file, press enter to select it`;
      } else {
        const quantitySuffix = res.truncatedSearch ? '+' : '';
        resultStatus.innerText = `Found ${matches.length}${quantitySuffix} files${!res.truncatedSearch ? '' : '. Some results may be excluded due to large amount of hits, please enter a more detailed query.'}`;
      }

      matches.forEach((match, matchIndex) => {
        const row = add(resultArea, 'div');
        rowList.push(row);
        row.tabIndex = 0;
        row.classList.add('fileMatch');
        row.innerText = match;
        const onclick = () => {
          cleanup();
          workspace.setActiveWorkspacePath(match);
        }
        row.onclick = onclick;
        row.onkeydown = (e) => {
          if (e.key === 'Enter') {
            onclick();
          } else if (e.key === 'ArrowDown' && matchIndex !== (matches.length - 1)) {
            e.preventDefault();
            rowList[matchIndex+1]?.focus();
          } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            if (matchIndex > 0) {
              rowList[matchIndex-1]?.focus();
            } else {
              input.focus();
            }
          }
        }
      })
    })()
      .catch(err => {
        console.warn('Failed search', err);
        resultStatus.innerText = `Failed search, please try again`;
      })
      .finally(() => {
        requestInProgress = false;
        if (nextRequestQuery) {
          const next = nextRequestQuery;
          nextRequestQuery = null;
          updateResultArea(next);
        }
      })
  };
  updateResultArea();

  input.focus();
  setTimeout(() => {
    input.focus();
    input.select();
  }, 50)

  const updateDispatcher = env.createCullingTaskSubmitter(150);
  input.addEventListener('input', () => {
    updateDispatcher.submit(updateResultArea);
  });


  const cleanup = () => {
    root.remove();
    window.removeEventListener('mousedown', cleanup);
    window.removeEventListener('keydown', stopAdditionalWindow, true);
  }
  root.addEventListener('mousedown', (e) => {
    e.stopPropagation();
    e.stopImmediatePropagation();
  });
  root.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
      cleanup();
    }
  });
  const stopAdditionalWindow = createDisplayFuzzyFinderShortcutListener(() => { /* Noop, we want to block the default shortcut from running */ })
  window.addEventListener('keydown', stopAdditionalWindow, true);
  window.addEventListener('mousedown', cleanup);
}

const createDisplayFuzzyFinderShortcutListener = (callback: () => void) => (e: KeyboardEvent) => {
  if ((e.metaKey || e.ctrlKey) && e.key === 'p') {
    e.preventDefault();
    e.stopPropagation();
    callback();
  }
}

export { createDisplayFuzzyFinderShortcutListener }
export default displayFuzzyWorkspaceFileFinder;

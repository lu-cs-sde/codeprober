
type FileTreeEntry<T> = { name: string } & (
    { type: 'file', value: TextFile<T> }
  | { type: 'dir', value: Directory<T> }
);

interface TextFile<T> {
  getContent: () => Promise<T | null>;
  getCachedContent: () => T | null;
  setCachedContent: (val: T) => void;
  setChangeListener: (callback: () => void) => void;
  isRemoved: () => boolean;
  fullPath: string,
}

// Private extensions of the file/dir types
type Invalidatable = { invalidate: () => void; onRemoved: () => void; };
type InvalidatableTextFile<T> = TextFile<T> & Invalidatable;
type InvalidatableDirectory<T> = Directory<T> & Invalidatable & {
  getMemoizedChildren: () => InvalidatableTreeEntry<T>[] | null
};
type InvalidatableTreeEntry<T> = { name: string } & (
      { type: 'file', value: InvalidatableTextFile<T> }
    | { type: 'dir', value: InvalidatableDirectory<T>}
);

type DirectoryState = 'not_loaded' | 'loaded' | 'removed';
interface Directory<T> {
  getState: () => DirectoryState;
  getChildren: () => Promise<FileTreeEntry<T>[] | null>;
  setChangeListener: (callback: () => void) => void;
  fullPath: string,
}

interface FileTreeManager<T> {
  notifyChanges: (paths: string[]) => void;
  root: Directory<T>;
  lookupCached: (path: string) => FileTreeEntry<T> | null;
  lookup: (path: string) => Promise<FileTreeEntry<T> | null>;
}

type DirectoryListEntry = {
  type: 'directory' | 'file',
  value: string,
}

interface FileTreeManagerInitArgs<T> {
  listDirectory: (prefix: string) => Promise<DirectoryListEntry[] | null>;
  getFileContent: (path: string) => Promise<T | null>;
}

const setupFileTreeManager = <T>(args: FileTreeManagerInitArgs<T>): FileTreeManager<T> => {
  type TTextFile = InvalidatableTextFile<T>;
  type TDirectory = InvalidatableDirectory<T>;
  type TFileManager = FileTreeManager<T>

  const createFile = (path: string): TTextFile => {
    let memoizedContent: T | null = null;
    let changeListener = () => {};
    let dirty: boolean = false;
    let isRemoved = false;
    const getContent: TTextFile['getContent'] = async () => {
      if (memoizedContent !== null && !dirty) {
        return memoizedContent;
      }
      const freshContent = await args.getFileContent(path);
      memoizedContent = freshContent;
      if (dirty) {
        dirty = false;
        // We went from a non-idle state to loading a change. Notify listeners!
        changeListener();
      }
      return freshContent;
    }
    const invalidate: TTextFile['invalidate'] = () => {
      if (!dirty && !memoizedContent) {
        // We are not loaded, nothing to invalidate
        return;
      }
      dirty = true;
      getContent().catch((err) => {
        console.warn('Failed fetching contents after change for', path, err)
      });
    }
    const getCachedContent: TTextFile['getCachedContent'] = () => memoizedContent;
    const setCachedContent: TTextFile['setCachedContent'] = (content) => {
      memoizedContent = content;
    };
    const onRemoved: TTextFile['onRemoved'] = () => {
      isRemoved = true;
    };
    return {
      getContent,
      getCachedContent,
      setCachedContent,
      setChangeListener: (callback) => { changeListener = callback; },
      invalidate,
      onRemoved,
      isRemoved: () => isRemoved,
      fullPath: path,
    };
  };

  const createDirectory = (path: string): TDirectory => {
    let state: DirectoryState = 'not_loaded';
    let memoizedChildren: InvalidatableTreeEntry<T>[] | null = null;
    let changeListener = () => {};
    let dirty = false;
    let dirtyCounter = 0;
    let getChildrenPromise: ReturnType<TDirectory['getChildren']> | null = null;
    const getChildren: TDirectory['getChildren'] = () => {
      if (memoizedChildren && !dirty) {
        return Promise.resolve(memoizedChildren);
      }

      if (getChildrenPromise === null) {
        getChildrenPromise = (async (): Promise<FileTreeEntry<T>[] | null> => {
          const expectedDirtyCounter = dirtyCounter;
          const listing = await args.listDirectory(path);
          getChildrenPromise = null;
          if (dirtyCounter !== expectedDirtyCounter) {
            // We were invalidated at least once during the loading, try again!
            return getChildren();
          }
          if (!listing) {
            return null;
          }
          const prevChildren = memoizedChildren ?? [];
          memoizedChildren = listing.map(v => {
            const name = v.value;
            const prev = prevChildren.find(x => x.name === name);
            if (prev) {
              return prev;
            }
            const fullPath = path ? `${path}/${name}` : name;
            return v.type === 'directory'
            ? { type: 'dir', name, value: createDirectory(fullPath) }
            : { type: 'file', name, value: createFile(fullPath) }
          });
          prevChildren.forEach(prev => {
            if (!listing.some(x => x.value === prev.name)) {
              // It is gone!
              prev.value.onRemoved();
            }
          })
          state = 'loaded';
          if (dirty) {
            dirty = false;
            // We went from a non-idle state to loading a change. Notify listeners!
            changeListener();
          }
          return memoizedChildren;
        })()
          .catch((err) => {
            console.warn('Failed loading dir', path, err);
            getChildrenPromise = null;
            throw err;
          });
      }
      return getChildrenPromise;
    }
    const invalidate: TDirectory['invalidate'] = () => {
      if (state === 'removed') {
        // Ignore
        return;
      }
      if (state === 'not_loaded') {
        // Ignore, we have not loaded anything yet
        return;
      }
      dirty = true;
      ++dirtyCounter;
      getChildren().catch((err) => {
        console.warn('Failed listing dir after change for', path, err)
      });
    }
    const onRemoved: TDirectory['onRemoved'] = () => {
      state = 'removed';
      memoizedChildren?.forEach(ch => ch.value.onRemoved());
    }
    return {
      getState: () => state,
      getChildren,
      setChangeListener: (callback) => { changeListener = callback; },
      invalidate,
      getMemoizedChildren: () => memoizedChildren,
      onRemoved,
      fullPath: path,
    };
  }

  const root: TDirectory = createDirectory('');
  const getCachedDir = (path: string): TDirectory | null => {
    let from = root;
    if (path) {
      const segments = path.split('/');
      for (let i = 0; i < segments.length; ++i) {
        const next = from.getMemoizedChildren()?.find(ch => ch.name === segments[i]);
        if (next?.type !== 'dir') {
          return null;
        }
        from = next?.value;
      }
    }
    return from;
  };

  const notifyChanges: TFileManager['notifyChanges'] = (paths) => {
    paths.forEach(path => {
      const segments = path.split('/');
      const dir = getCachedDir(segments.slice(0, -1).join('/'));
      if (!dir) {
        // If we have the directory structure:
        //   a { b { c } }
        // Then this is a notification for 'c', and we have not yet opened 'b'
        // However, we may have opened something above it in the tree, like 'a'
        // The nearest opened place needs to be invalidated
        for (let end = segments.length - 2; end >= 0; --end) {
          const parent = getCachedDir(segments.slice(0, end).join('/'));
          if (parent) {
            parent.invalidate();
            return;
          }
        }
        return;
      }
      const specificFile = dir.getMemoizedChildren()?.find(x => x.name === segments[segments.length - 1]);
      if (specificFile) {
        specificFile.value.invalidate();
      } else {
        // Either a new file apeared in the dir, or it disappeared
        // We must flush the parent dir to find out
        dir.invalidate();
      }
    });
  };

  const lookupCached: TFileManager['lookupCached'] = (path) => {
    if (!path) {
      return { type: 'dir', name: '', value: root }
    }
    let dir = root;
    const segments = path.split('/');
    for (let i = 0; i < segments.length - 1; ++i) {
      const seg = segments[i];

      const child = dir.getMemoizedChildren()?.find(x => x.name === seg && x.type === 'dir');
      if (child?.type !== 'dir') {
        return null;
      }
      dir = child.value;
    }
    return dir.getMemoizedChildren()?.find(x => x.name === segments[segments.length - 1]) ?? null;
  }

  const lookup: TFileManager['lookup'] = async (path) => {
    if (!path) {
      return { type: 'dir', name: '', value: root }
    }
    let dir: Directory<T> = root;
    const segments = path.split('/');
    for (let i = 0; i < segments.length - 1; ++i) {
      const seg = segments[i];

      const child = (await dir.getChildren())?.find(x => x.name === seg && x.type === 'dir');
      if (child?.type !== 'dir') {
        return null;
      }
      dir = child.value;
    }
    return (await dir.getChildren())?.find(x => x.name === segments[segments.length - 1]) ?? null;
  }

  return { notifyChanges, root, lookupCached, lookup };
};

export { setupFileTreeManager, FileTreeManagerInitArgs, TextFile, Directory, DirectoryListEntry }
export default FileTreeManager;

/// <reference types="node" />
import { describe, it } from 'node:test';
import assert from 'node:assert';

import { setupFileTreeManager, FileTreeManagerInitArgs, Directory, TextFile, DirectoryListEntry } from '../../src/model/FileTreeManager';
const sleep = (ms: number) => new Promise(res => setTimeout(res, ms));


const setupTest = () => {
  const tree: any = {
    foo: {
      bar: 'baz',
      lorem: {
        ipsum: 'dolor',
        sit: 'amet'
      }
    }
  }
  const lookupEntry = (path: string) => {
    let ret = tree;
    if (path) {
      path.split('/').forEach(part => {
        ret = ret[part] || {};
      })
    }
    return ret;
  }
  const listDirectory: FileTreeManagerInitArgs<string>['listDirectory'] = async (path) => {
    return Object.entries(lookupEntry(path)).map<DirectoryListEntry>(([name, val]) => {
      if (typeof val === 'string') {
        return { type: 'file', value: { name, readOnly: false } };
      }
      return { type: 'directory', value: name };
    });
  }
  const getFileContent: FileTreeManagerInitArgs<string>['getFileContent'] = async (path) => {
    const segments = path.split('/');
    const dir = lookupEntry(segments.slice(0, -1).join('/'));
    const val = dir[segments[segments.length - 1]];
    return typeof val === 'string' ? val : null;
  }
  const mgr = setupFileTreeManager({ listDirectory, getFileContent });
  return { tree, listDirectory, getFileContent, mgr };
}

describe('FileTreeManager', () => {
  it('should notify on active dir change', async () => {
    const { mgr } = setupTest();

    let callbacks = 0;
    mgr.root.setChangeListener(() => {
      ++callbacks;
    })

    assert.equal(callbacks, 0); // No callback yet

    mgr.notifyChanges(['']); // Change in root directory
    await sleep(1);
    assert.equal(callbacks, 0); // Still no callback, because root file is idle
    assert.equal(mgr.root.getState(), 'not_loaded');

    await mgr.root.getChildren();

    mgr.notifyChanges(['']);
    await sleep(1);
    assert.equal(callbacks, 1); // Now there is a callback
    assert.equal(mgr.root.getState(), 'loaded');

    mgr.notifyChanges(['seemingly/unrelated/path']);
    await sleep(1);
    // Believe it or not, one more change expected. This is because "seemingly" is an entry in the root dir, which we have opened
    assert.equal(callbacks, 2);
  });

  it('should be able to get text content', async () => {
    const { tree, mgr } = setupTest();

    const dir = (await mgr.root.getChildren())?.find(x => x.name === 'foo' && x.type === 'dir')?.value as Directory<string>;
    const bar = (await dir.getChildren())?.find(x => x.name === 'bar')?.value as TextFile<string>;

    let callbacks = 0;
    bar.setChangeListener(() => {
      ++callbacks;
    });
    assert.equal(await bar.getContent(), 'baz');
    tree.foo.bar = 'changed!';
    await sleep(1);
    assert.equal(await bar.getContent(), 'baz'); // Cached, change not visible yet

    mgr.notifyChanges(['foo/bar']);
    await sleep(1);
    assert.equal(await bar.getContent(), 'changed!');
  });

  it('should handle nested directory changes', async () => {
    const { tree, mgr } = setupTest();

    // Load the nested structure
    const fooDir = (await mgr.root.getChildren())?.find(x => x.name === 'foo' && x.type === 'dir')?.value as Directory<string>;
    const loremDir = (await fooDir.getChildren())?.find(x => x.name === 'lorem')?.value as Directory<string>;

    let loremCallbacks = 0;
    loremDir.setChangeListener(() => {
      ++loremCallbacks;
    });
    // Load children first..
    await loremDir.getChildren();
    // ..and then add a new entry to the tree
    tree.foo.lorem.newFile = 'new content';

    // Notify about the new file
    mgr.notifyChanges(['foo/lorem/newFile']);
    await sleep(1);

    // The lorem directory should have been notified of the change
    assert.equal(loremCallbacks, 1);

    // Should be able to see the new file
    const children = await loremDir.getChildren();
    const newFile = children?.find(x => x.name === 'newFile');
    assert(newFile, 'New file should be found');
    assert.equal(newFile.type, 'file');
  });

  it('should handle file change notifications when file is not yet loaded', async () => {
    const { tree, mgr } = setupTest();

    // Change a file before loading anything
    tree.foo.bar = 'early change';

    // Notify about the change
    mgr.notifyChanges(['foo/bar']);
    await sleep(1);

    // Now load the structure and verify the changed content is visible
    const fooDir = (await mgr.root.getChildren())?.find(x => x.name === 'foo' && x.type === 'dir')?.value as Directory<string>;
    const bar = (await fooDir.getChildren())?.find(x => x.name === 'bar')?.value as TextFile<string>;

    assert.equal(await bar.getContent(), 'early change');
  });

  it('should handle notifications for parent directories when child is not cached', async () => {
    const { tree, mgr } = setupTest();

    // Load only the root
    const rootChildren = await mgr.root.getChildren();

    let rootCallbacks = 0;
    mgr.root.setChangeListener(() => {
      ++rootCallbacks;
    });

    let fooCallbacks = 0;
    (rootChildren?.find(x => x.name === 'foo')?.value as Directory<string>)?.setChangeListener(() => {
      ++fooCallbacks;
    })

    // Change a deeply nested file
    tree.foo.lorem.ipsum = 'deep change';

    // Notify about the deep change
    mgr.notifyChanges(['foo/lorem/ipsum']);
    await sleep(1);

    // No callbacks because foo is not loaded yet
    assert.equal(rootCallbacks, 0);
    assert.equal(fooCallbacks, 0);

    // Notify about semideep change
    mgr.notifyChanges(['foo/shallower']);
    await sleep(1);

    assert.equal(rootCallbacks, 0);
    assert.equal(fooCallbacks, 0);

    // Notify about shallow change
    mgr.notifyChanges(['shallowest']);
    await sleep(1);

    assert.equal(rootCallbacks, 1);
    assert.equal(fooCallbacks, 0);
  });

  it('should ignore changes on unloaded file', async () => {
    const { mgr } = setupTest();

    const fooDir = (await mgr.root.getChildren())?.find(x => x.name === 'foo' && x.type === 'dir')?.value as Directory<string>;
    const bar = (await fooDir.getChildren())?.find(x => x.name === 'bar')?.value as TextFile<string>;

    let callbacks = 0;
    bar.setChangeListener(() => { ++callbacks; });

    assert.equal(callbacks, 0); // Initial state: no change
    mgr.notifyChanges(['foo/bar']); await sleep(1);
    assert.equal(callbacks, 0); // File is idle, so no change

    await bar.getContent();
    mgr.notifyChanges(['foo/bar']); await sleep(1);
    assert.equal(callbacks, 1); // File is no longer idle, so a change is received.
  });

  it('should handle multiple file change listeners', async () => {
    const { tree, mgr } = setupTest();

    const fooDir = (await mgr.root.getChildren())?.find(x => x.name === 'foo' && x.type === 'dir')?.value as Directory<string>;
    const bar = (await fooDir.getChildren())?.find(x => x.name === 'bar')?.value as TextFile<string>;
    await bar.getContent();

    let callbacks1 = 0;
    let callbacks2 = 0;

    bar.setChangeListener(() => { ++callbacks1; });
    bar.setChangeListener(() => { ++callbacks2; }); // This should replace the first listener

    tree.foo.bar = 'multiple listeners test';
    mgr.notifyChanges(['foo/bar']); await sleep(1);

    // Only the second listener should have been called
    assert.equal(callbacks1, 0);
    assert.equal(callbacks2, 1);
  });

  it('should cache directory children and only reload when invalidated', async () => {
    const { tree, mgr } = setupTest();

    const children1 = (await mgr.root.getChildren())!;
    const children2 = (await mgr.root.getChildren())!;

    // Should return the same cached result
    assert.equal(children1.length, children2.length);
    assert.equal(children1[0].name, children2[0].name);

    // Add a new top-level item
    tree.newDir = { newFile: 'content' };

    // Should still return cached result
    const children3 = (await mgr.root.getChildren())!;
    assert.equal(children3.length, children1.length);

    // After notification, should see the new item
    mgr.notifyChanges(['newDir']);
    await sleep(1);
    const children4 = (await mgr.root.getChildren())!;
    assert.equal(children4.length, children1.length + 1);
    assert(children4.find(x => x.name === 'newDir'));
  });

});

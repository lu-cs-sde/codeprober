
// From https://github.com/llimllib/pymag-trees/blob/9279ff43bdb6321ac336be8871ad3768f8b5e2e3/buchheim.py
// Initially translated to TypeScript with ChatGPT, some manual changes applied afterwards, such as adding a type parameter to DrawTree.

import { ListedTreeNode } from '../../protocol';

interface NodeWithChild<T> {
  children: { type: 'children'; value: T[] }
          | { type: 'placeholder'; value: number }
          ;
};

class DrawTree<T extends NodeWithChild<T>> {
  x: number = -1.0;
  y: number;
  tree: T;
  children: DrawTree<T>[];
  parent: DrawTree<T> | null;
  thread: DrawTree<T> | null = null;
  mod: number = 0;
  ancestor: DrawTree<T>;
  change: number = 0;
  shift: number = 0;
  private _lmost_sibling: DrawTree<T> | null = null;
  number: number;

  constructor(tree: T, parent: DrawTree<T> | null = null, depth: number = 0, number: number = 1) {
      this.y = depth;
      this.tree = tree;
      this.parent = parent;
      this.number = number;
      switch (tree.children.type) {
        case 'children': {
          this.children = (tree.children.type === 'children' ? tree.children.value : []).map((c: any, i: number) => new DrawTree(c, this, depth + 1, i + 1));
          break;
        }
        case 'placeholder': {
          // Need to make room for one simulated placeholder child
          if (number === -1) {
            // ..unless we are the placeholder
            this.children = [];
          } else {
            this.children = [new DrawTree(tree, this, depth, -1)];
          }
          break;
        }
      }
      this.ancestor = this;
  }

  left(): DrawTree<T> | null {
      return this.thread || (this.children.length > 0 ? this.children[0] : null);
  }

  right(): DrawTree<T> | null {
      return this.thread || (this.children.length > 0 ? this.children[this.children.length - 1] : null);
  }

  lbrother(): DrawTree<T> | null {
      let n: DrawTree<T> | null = null;
      if (this.parent) {
          for (const node of this.parent.children) {
              if (node === this) {
                  return n;
              } else {
                  n = node;
              }
          }
      }
      return n;
  }

  get lmost_sibling(): DrawTree<T> | null {
      if (!this._lmost_sibling && this.parent && this !== this.parent.children[0]) {
          this._lmost_sibling = this.parent.children[0];
      }
      return this._lmost_sibling;
  }

  toString(): string {
      return `${this.tree}: x=${this.x} mod=${this.mod}`;
  }
}

function buchheim<T extends NodeWithChild<T>>(tree: any): DrawTree<T> {
  const dt = firstwalk(new DrawTree(tree));
  const min = second_walk(dt);
  if (min < 0) {
      third_walk(dt, -min);
  }
  return dt;
}

function third_walk<T extends NodeWithChild<T>>(tree: DrawTree<T>, n: number): void {
  tree.x += n;
  for (const c of tree.children) {
      third_walk(c, n);
  }
}

function firstwalk<T extends NodeWithChild<T>>(v: DrawTree<T>, distance: number = 1.0): DrawTree<T> {
  if (v.children.length === 0) {
      if (v.lmost_sibling) {
          v.x = (v.lbrother()?.x ?? 0) + distance;
      } else {
          v.x = 0.0;
      }
  } else {
      let default_ancestor = v.children[0];
      for (const w of v.children) {
          firstwalk(w);
          default_ancestor = apportion(w, default_ancestor, distance);
      }
      execute_shifts(v);

      const midpoint = (v.children[0].x + v.children[v.children.length - 1].x) / 2;
      const w = v.lbrother();
      if (w) {
          v.x = w.x + distance;
          v.mod = v.x - midpoint;
      } else {
          v.x = midpoint;
      }
  }
  return v;
}

function apportion<T extends NodeWithChild<T>>(v: DrawTree<T>, default_ancestor: DrawTree<T>, distance: number): DrawTree<T> {
  const w = v.lbrother();
  if (w) {
      let vir = v, vor = v;
      let vil = w;
      let vol = v.lmost_sibling!;
      let sir = v.mod, sor = v.mod;
      let sil = vil.mod, sol = vol.mod;

      while (vil.right() && vir.left()) {
          vil = vil.right()!;
          vir = vir.left()!;
          vol = vol.left()!;
          vor = vor.right()!;
          vor.ancestor = v;

          const shift = (vil.x + sil) - (vir.x + sir) + distance;
          if (shift > 0) {
              move_subtree(ancestor(vil, v, default_ancestor), v, shift);
              sir += shift;
              sor += shift;
          }

          sil += vil.mod;
          sir += vir.mod;
          sol += vol.mod;
          sor += vor.mod;
      }

      if (vil.right() && !vor.right()) {
          vor.thread = vil.right();
          vor.mod += sil - sor;
      } else if (vir.left() && !vol.left()) {
          vol.thread = vir.left();
          vol.mod += sir - sol;
      }

      default_ancestor = v;
  }
  return default_ancestor;
}

function move_subtree<T extends NodeWithChild<T>>(wl: DrawTree<T>, wr: DrawTree<T>, shift: number): void {
  const subtrees = wr.number - wl.number;
  const ratio = shift / subtrees;
  wr.change -= ratio;
  wr.shift += shift;
  wl.change += ratio;
  wr.x += shift;
  wr.mod += shift;
}

function execute_shifts<T extends NodeWithChild<T>>(v: DrawTree<T>): void {
  let shift = 0;
  let change = 0;
  for (let i = v.children.length - 1; i >= 0; i--) {
      const w = v.children[i];
      w.x += shift;
      w.mod += shift;
      change += w.change;
      shift += w.shift + change;
  }
}

function ancestor<T extends NodeWithChild<T>>(vil: DrawTree<T>, v: DrawTree<T>, default_ancestor: DrawTree<T>): DrawTree<T> {
  return v.parent && v.parent.children.includes(vil.ancestor) ? vil.ancestor : default_ancestor;
}

function second_walk<T extends NodeWithChild<T>>(v: DrawTree<T>, m: number = 0, depth: number = 0, min: number | null = null): number {
  v.x += m;
  v.y = depth;

  if (min === null || v.x < min) {
      min = v.x;
  }

  for (const w of v.children) {
      min = second_walk(w, m + v.mod, depth + 1, min);
  }

  return min;
}

function layoutTree(tree: ListedTreeNode): DrawTree<ListedTreeNode> {
  return buchheim(tree);
}
export default layoutTree;
export { DrawTree }

// import { oneDark } from '@codemirror/theme-one-dark';
import { EditorView, basicSetup } from "codemirror"
import { closeHoverTooltips, Decoration, hoverTooltip, keymap, ViewPlugin, WidgetType } from "@codemirror/view"
import { indentWithTab } from '@codemirror/commands';
import { vscodeDark, vscodeLight } from '@uiw/codemirror-theme-vscode';
import {javaLanguage} from "@codemirror/lang-java"
import {rustLanguage} from "@codemirror/lang-rust"
import {cppLanguage} from "@codemirror/lang-cpp"
import {pythonLanguage} from "@codemirror/lang-python"
import { Compartment, StateEffect, StateField } from '@codemirror/state';
import { linter, forceLinting } from '@codemirror/lint';
import { CompletionContext, autocompletion, selectedCompletion } from '@codemirror/autocomplete';

function testSetup() {

  let themeCompartment = new Compartment();

  let editor = new EditorView({
    extensions: [
        basicSetup,
        keymap.of([indentWithTab]),
        themeCompartment.of(vscodeDark),
        java(),
    ],
    parent: document.body,
    doc: [`
package pkg;

class Foo extends Bar {
      void baz() { }
}`
].join('\n'),
  });

setTimeout(() => {
  editor.dispatch({
    effects: themeCompartment.reconfigure(vscodeDark)
  });

  setTimeout(() => {
    editor.dispatch({
      effects: themeCompartment.reconfigure(vscodeLight)
    });

  }, 2000)
}, 2000)
// linter(() => [], { })

autocompletion({ })
}
Decoration.widget({}).ran
// Decoration.mark({ attributes: {}})
const languages = {
  java: () => javaLanguage,
  rust: () => rustLanguage,
  cpp: () => cppLanguage,
  c: () => cppLanguage,
  python: () => pythonLanguage,
}
export default { EditorView, basicSetup, testSetup, Compartment, languages, vscodeDark, vscodeLight, indentWithTab, keymap, StateEffect, Decoration, StateField, linter, forceLinting, WidgetType, ViewPlugin, CompletionContext, autocompletion, selectedCompletion, hoverTooltip, closeHoverTooltips }


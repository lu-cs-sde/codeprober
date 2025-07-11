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
import { CompletionContext, autocompletion, completionStatus, selectedCompletion } from '@codemirror/autocomplete';

const languages = {
  java: () => javaLanguage,
  rust: () => rustLanguage,
  cpp: () => cppLanguage,
  c: () => cppLanguage,
  python: () => pythonLanguage,
}
export default { EditorView, basicSetup, Compartment, languages, vscodeDark, vscodeLight, indentWithTab, keymap, StateEffect, Decoration, StateField, linter, forceLinting, WidgetType, ViewPlugin, CompletionContext, autocompletion, selectedCompletion, hoverTooltip, closeHoverTooltips, completionStatus }


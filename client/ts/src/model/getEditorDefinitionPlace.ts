import EditorInitializer from './EditorInitializer';

interface EditorDefinitionPlace {
  defineEditor: (id: string, preload: EditorPreloader, init: EditorInitializer) => void;
  definedEditors: {
    [editorId: string]: {
      preload: EditorPreloader;
      init: EditorInitializer;
    };
  };
  loadPreload: (preloader: EditorPreloader, onDone: () => void) => void;
}

const getEditorDefinitionPlace = (): EditorDefinitionPlace => window as any;

export default getEditorDefinitionPlace;

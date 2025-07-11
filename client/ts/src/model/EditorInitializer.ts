import ModalEnv from './ModalEnv';

type EditorInitializer = (initialValue: string, onChange: (newValue: string, adjusters?: LocationAdjuster[] ) => void, initialSyntaxHighlight: SyntaxHighlightingLanguageId) => {
  setLocalState?: (newValue: string) => void;
  getLocalState?: () => string;
  updateSpanHighlight?: (baseHighlight: Span | null, stickyHighlights: StickyHighlight[]) => void;
  markText?: TextMarkFn;
  themeToggler: (isLightTheme: boolean) => void;
  syntaxHighlightingToggler: (langId: SyntaxHighlightingLanguageId) => void;
  registerModalEnv?: (env: ModalEnv) => void;
};

export default EditorInitializer;

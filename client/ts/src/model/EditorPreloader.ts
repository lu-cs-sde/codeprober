
type EditorPreloader = () => {
  script: string[];
  style: string[];
  predicate: () => boolean;
};

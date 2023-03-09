

type ColorType =
    'window-border'
  | 'probe-result-area'
  | 'syntax-type'
  | 'syntax-attr'
  | 'syntax-modifier'
  | 'syntax-variable'
  | 'separator'
  | 'ast-node-bg'
  | 'ast-node-bg-hover';

const lightColors: Record<ColorType, string> = {
  'window-border': '#999',
  'probe-result-area': '#F4F4F4',
  'syntax-type': '#267F99',
  'syntax-attr': '#795E26',
  'syntax-modifier': '#0000FF',
  'syntax-variable': '#001080',
  'separator': '#000',
  'ast-node-bg': '#DDD',
  'ast-node-bg-hover': '#AAA',
};

const darkColors: typeof lightColors = {
  'window-border': '#999',
  'probe-result-area': '#333',
  'syntax-type': '#4EC9B0',
  'syntax-attr': '#DCDCAA',
  'syntax-modifier': '#569CD6',
  'syntax-variable': '#9CDCFE',
  'separator': '#FFF',
  'ast-node-bg': '#4A4A4A',
  'ast-node-bg-hover': '#7D7D7D',
};

const getThemedColor = (lightTheme: boolean, type: ColorType): string => {
  return (lightTheme ? lightColors : darkColors)[type];
}

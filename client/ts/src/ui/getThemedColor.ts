

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

type Color = {
  get opaque(): string;
  get dimmed(): string;
  maybeDimmed(getDimmed: boolean): string;
}

const mkCol = (rgb: string): Color => {
  const opaque = `#${rgb}`;
  const dimmed = `#${rgb}40`;
  return {
    opaque,
    dimmed,
    maybeDimmed: (gd) => gd ? dimmed : opaque,
  }
}

const mkPredimmed = (opaque: string, dimmed: string): Color => ({
  opaque, dimmed, maybeDimmed: (gd) => gd ? dimmed : opaque
});

const lightColors: Record<ColorType, Color> = {
  'window-border': mkCol('999999'),
  'probe-result-area': mkCol('F4F4F4'),
  'syntax-type': mkCol('267F99'),
  'syntax-attr': mkCol('795E26'),
  'syntax-modifier': mkCol('0000FF'),
  'syntax-variable': mkCol('001080'),
  'separator': mkPredimmed('#000000', '#B6B6B6'),
  'ast-node-bg': mkCol('DDDDDD'),
  'ast-node-bg-hover': mkCol('AAAAAA'),
};

const darkColors: typeof lightColors = {
  'window-border': mkCol('999999'),
  'probe-result-area': mkCol('333333'),
  'syntax-type': mkCol('4EC9B0'),
  'syntax-attr': mkCol('DCDCAA'),
  'syntax-modifier': mkCol('569CD6'),
  'syntax-variable': mkCol('9CDCFE'),
  'separator': mkPredimmed('#FFFFFF', '#666666'),
  'ast-node-bg': mkCol('1C1C1C'),
  'ast-node-bg-hover': mkCol('666666'),
};

const getThemedColor = (lightTheme: boolean, type: ColorType): Color => {
  return (lightTheme ? lightColors : darkColors)[type];
}

export default getThemedColor;


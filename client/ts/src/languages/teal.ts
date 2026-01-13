/*---------------------------------------------------------------------------------------------
 *  Copyright (c) Microsoft Corporation, Christoph Reichenbach.  All rights reserved.
 *  Licensed under the MIT License. See License.txt in the project root for license information.
 *--------------------------------------------------------------------------------------------*/

export const tealTokens = {
  keywords: [
    'fun', 'var', 'while', 'if', 'else', 'return',
    'not', 'and', 'or', 'null', 'new',
    'for', 'in', 'qualifier', 'type',
    'assert',
    'class', 'self'
  ],

  typeKeywords: [
    'int', 'string', 'array', 'any', 'nonnull'
  ],

  operators: [
    '+', '-', '*', '/', '%',
    ':=',
    '>', '<', '==', '<=', '>=', '!=', '=',
    ':', '<:'
  ],
  brackets: [['{', '}', 'delimiter.curly'],
             ['[', ']', 'delimiter.square'],
             ['(', ')', 'delimiter.parenthesis']],

  // we include these common regular expressions
  symbols:  /[=><!~?:&|+\-*\/\^%]+/,

  escapes: /\\(?:[\\"])/,

  // The main tokenizer for our languages
  tokenizer: {
    root: [
      // identifiers and keywords
      [/[a-zA-Z_][a-zA-Z_0-9]*/, { cases: { '@typeKeywords': 'keyword',
                                            '@keywords': 'keyword',
                                            '@default': 'identifier' } }],
      [/[A-Z][a-zA-Z_0-9]*/, 'type.identifier' ],  // to show class names nicely

      // whitespace
      { include: '@whitespace' },

      // delimiters and operators
      [/[{}()\[\]]/, '@brackets'],
      [/@symbols/, { cases: { '@operators': 'operator',
                              '@default'  : '' } } ],

      // // @ annotations.
      // // As an example, we emit a debugging log message on these tokens.
      // // Note: message are supressed during the first load -- change some lines to see them.
      // [/@\s*[a-zA-Z_\$][\w\$]*/, { token: 'annotation', log: 'annotation token: $0' }],

      // numbers
      [/\d+/, 'number'],

      // delimiter: after number because of .\d floats
      [/[;,.]/, 'delimiter'],

      // strings
      [/"([^"\\]|\\.)*$/, 'string.invalid' ],  // non-teminated string
      [/"/,  { token: 'string.quote', bracket: '@open', next: '@string' } ],

      // characters
      [/'[^\\']'/, 'string'],
      [/(')(@escapes)(')/, ['string','string.escape','string']],
      [/'/, 'string.invalid']
    ],

    comment: [
      [/[^\/*]+/, 'comment'],
      [/\*\//, 'comment', '@pop'],
      [/[\/*]/, 'comment']
    ],

    string: [
      [/[^\\"]+/,  'string'],
      [/@escapes/, 'string.escape'],
      [/\\./,      'string.escape.invalid'],
      [/"/,        { token: 'string.quote', bracket: '@close', next: '@pop' } ]
    ],

    whitespace: [
      [/[ \t\r\n]+/, ''],
      [/\/\*/, 'comment', '@comment'],
      [/\/\/.*$/, 'comment']
    ],
  },
};

const tealConf = {
  comments: {
    lineComment: '//',
    blockComment: ['/*', '*/']
  },
  brackets: [
    ['{', '}'],
    ['[', ']'],
    ['(', ')']
  ],
}

export const tealInit = (editorType: string) => {
  if (editorType == 'Monaco') {
    var languages = (window as any).monaco.languages;
    languages.register({ id : 'teal' });
    languages.setMonarchTokensProvider('teal', tealTokens);
    languages.setLanguageConfiguration('teal', tealConf);
  }
};

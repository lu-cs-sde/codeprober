

function initDemoIframe() {
  const parseAddNum = (() => {
    const makeNode = (type, start, end, props = {}) => {
      const ret = ({ type, start, end, children: [], props})
      ret.addChildren = (...children) => {
        ret.children.push(...children);
        children.forEach(child => child.parent = ret);
      }
      return ret;
    };

    class ParseError extends Error {
      constructor(message, line, column) {
        super(message);
        this.name = 'ParseError';
        this.line = line;
        this.column = column;
        this.location = `${line}:${column}`;
      }

      toString() {
        return `${this.message} at ${this.location}`;
      }

      toDiagnostic() {
        const pos = (this.line << 12) + this.column;
        return { type: 'ERROR', start: pos, end: pos, msg: this.message, };
      }
    }

    function tokenize(srcText) {
      const tokens = [];
      let i = 0;
      let line = 1;
      let column = 1;

      function getCurrentPos() {
        return (line << 12) + column;
      }

      function advanceChar() {
        if (srcText[i] === '\n') {
          line++;
          column = 1;
        } else {
          column++;
        }
        i++;
      }

      while (i < srcText.length) {
        const char = srcText[i];

        if (/\s/.test(char)) {
          advanceChar();
          continue;
        }

        if (char === '/' && srcText[i + 1] === '/') {
          while (i < srcText.length && srcText[i] !== '\n') {
            advanceChar();
          }
          continue;
        }

        if (/\d/.test(char)) {
          let num = '';
          const startPos = getCurrentPos();
          while (i < srcText.length && /\d/.test(srcText[i])) {
            num += srcText[i];
            advanceChar();
          }
          const endPos = getCurrentPos() - 1;
          tokens.push({
            type: 'NUMBER',
            value: parseInt(num),
            start: startPos,
            end: endPos
          });
          continue;
        }

        if (char === '+') {
          const pos = getCurrentPos();
          tokens.push({
            type: 'PLUS',
            start: pos,
            end: pos
          });
          advanceChar();
          continue;
        }

        if (char === '(') {
          const pos = getCurrentPos();
          tokens.push({
            type: 'LPAREN',
            start: pos,
            end: pos
          });
          advanceChar();
          continue;
        }

        if (char === ')') {
          const pos = getCurrentPos();
          tokens.push({
            type: 'RPAREN',
            start: pos,
            end: pos
          });
          advanceChar();
          continue;
        }

        throw new ParseError(`Unexpected character: ${char}`, line, column);
      }

      return tokens;
    }
    return function parseAddNum(srcText) {
      const tokens = tokenize(srcText);
      let pos = 0;

      function getCurrentToken() {
        return pos < tokens.length ? tokens[pos] : null;
      }

      function getTokenLocation(token) {
        if (!token) return { line: 1, column: 1 };
        const line = token.start >> 12;
        const column = token.start & 0xFFF;
        return { line, column };
      }

      function parseExpression() {
        let left = parsePrimary();

        while (pos < tokens.length && tokens[pos].type === 'PLUS') {
          const opToken = tokens[pos++];
          const right = parsePrimary();

          const addNode = makeNode('Add', left.start, right.end, {
            value: () => addNode.children[0].props.value() + addNode.children[1].props.value(),
            lhs: () => addNode.children[0],
            rhs: () => addNode.children[1],
          });
          addNode.addChildren(left, right);
          left = addNode;
        }

        return left;
      }

      function parsePrimary() {
        if (pos >= tokens.length) {
          throw new ParseError('Unexpected end of input', 1, 1);
        }

        const token = tokens[pos];

        if (token.type === 'NUMBER') {
          pos++;
          return makeNode('Num', token.start, token.end, {
            value: () => token.value,
            isPrime: () => {
              const num = token.value;
              for(let i = 2, s = Math.sqrt(num); i <= s; i++) {
                  if(num % i === 0) return false;
              }
              return num > 1;
            }
          });
        }

        if (token.type === 'LPAREN') {
          pos++;
          const expr = parseExpression();

          if (pos >= tokens.length || tokens[pos].type !== 'RPAREN') {
            const currentToken = getCurrentToken();
            const location = getTokenLocation(currentToken);
            throw new ParseError('Expected closing parenthesis', location.line, location.column);
          }
          pos++;

          return expr;
        }

        const location = getTokenLocation(token);
        throw new ParseError(`Unexpected token: ${token.type}`, location.line, location.column);
      }

      return parseExpression();
    }
  })();

  const frame = document.getElementById('cpr-demo');
  const parse = (parsingRequestData) => {
    try {
      return { type: 'ok', value: parseAddNum(parsingRequestData.src.value) };
    } catch (e) {
      return { type: 'err', value: e };
    }
  };
  const nodeToLocator = (node) => {
    let steps = [];
    let parent = node.parent;
    while (parent) {
      steps.push({ type: 'child', value: parent.children.indexOf(node) });
      parent = parent.parent;
    }
    steps.reverse();
    return {
      result: { type: node.type, start: node.start, end: node.end, depth: steps.length },
      steps,
    };
  }
  const locatorToNode = (root, locator) => {
    let node = root;
    locator.steps.forEach(step => {
      // Must be child steps
      node = node.children[step.value];
    })
    return node;
  }
  frame.contentWindow.CPR_REQUEST_HANDLER = {
    submit: async (msg) => {
      switch (msg.type) {
        case 'wsput:init':
          return {
            info: {
              type: 'init',
              version: { hash: 'demo', clean: false, },
            },
          };

        case 'ListWorkspaceDirectory':
          return {};

        case 'ListNodes': {
          const pres = parse(msg.src);
          if (pres.type === 'err') {
            if (pres.value.toDiagnostic) {
              return {
                body: [{ type: 'stderr', value: `Error: ${pres.value}` }],
                errors: [pres.value.toDiagnostic()],
              };
            }
            return { body: [{ type: 'stderr', value: `Error: ${pres.value}` }] }
          }
          const result = [];
          const gatherNodes = (node) => {
            if (node.start <= msg.pos+1 && node.end >= msg.pos-1) {
              result.push(nodeToLocator(node));
            }
            node.children.forEach(gatherNodes);
          }
          gatherNodes(pres.value);
          return {
            nodes: result,
          }
        }
        case 'ListProperties': {
          const pres = parse(msg.src);
          if (pres.type === 'err') {
            return { body: [{ type: 'stderr', value: `Error: ${pres.value}` }] }
          }
          const root = pres.value;
          const subject = locatorToNode(root, msg.locator);
          if (!subject) {
            return { body: [{ type: 'stderr', value: 'No such node' }] }
          }
          return {
            properties: Object.keys(subject.props).map(name => ({ name }))
          }
        }

        case 'ListTreeUpwards': {
          return { body: [{ type: 'stdout', value: 'Unsupported in this demo version of CodeProber' }] }
        }
        case 'ListTreeDownwards': {
          const pres = parse(msg.src);
          if (pres.type === 'err') {
            return { body: [{ type: 'stderr', value: `Error: ${pres.value}` }] }
          }
          const root = pres.value;
          const subject = locatorToNode(root, msg.locator);
          if (!subject) {
            return { body: [{ type: 'stderr', value: 'No such node' }] }
          }
          const gatherNodes = (node, tgt) => {
            const children = [];
            node.children.forEach(ch => gatherNodes(ch, children));
            const ltn = { type: 'node', locator: nodeToLocator(node), children: { type: 'children', value: children } };
            if (node.type == 'Add') {
              children[0].name = 'lhs';
              children[1].name = 'lhs';
            }
            tgt.push(ltn);

          }

          const result = [];
          gatherNodes(root, result);
          return {
            body: [], node: result[0]
          }
        }

        case 'EvaluateProperty': {
          let body = [];
          let errors = [];
          const pres = parse(msg.src);
          let responseLocator =  msg.locator;
          if (pres.type === 'err') {
            if (pres.value.toDiagnostic) {
              errors.push(pres.value.toDiagnostic());
            }
            body.push({ type: 'plain', value: 'Invalid src' });
          } else {
            const root = pres.value;
            const subject = locatorToNode(root, msg.locator);
            if (!subject) {
              body.push({ type: 'stderr', value: 'No such node' });
            } else {
              responseLocator = nodeToLocator(subject);
              switch (msg.property.name) {
                case 'm:NodesWithProperty': {
                  // Query probe
                  let result = [];
                  const passesFilter = (node) => {
                    const args = msg.property.args;
                    if (!args) { return true; }
                    const propArg = args[0]?.value;
                    if (propArg && !node.props[propArg]) { return false; }
                    const predicates = args[1]?.value;
                    if (predicates && !predicates.split("&").every(pred => {
                      if (pred.startsWith("this<:")) {
                        return node.type === pred.slice('this<:'.length);
                      }
                      if (pred.startsWith('@lineSpan~=')) {
                        const expectedLine = Number.parseInt(pred.slice('@lineSpan~='.length));
                        return (node.start >> 12) <= expectedLine && (node.end >> 12) >= expectedLine;
                      }

                      // Else, property comparison
                      const eq = pred.indexOf('=');
                      if (!eq) { return false; }

                      const propName = pred.match(/([a-zA-Z:0-9]+).*/)[1]
                      if (!node.props[propName]) {
                        return false;
                      }
                      const actual = node.props[propName]();
                      const expected = pred.slice(eq + 1);

                      const invert = pred.charAt(eq-1) == '!';
                      if (invert) {
                        return actual != expected;
                      }
                      const contains = pred.charAt(eq-1) == '~';
                      if (contains) {
                        return actual.includes(expected);
                      }
                      return actual == expected;
                    })) {
                      return false;
                    }
                    return true;
                  };
                  const traverseNode = (node) => {
                    if (passesFilter(node)) {
                      result.push({ type: 'node', value: nodeToLocator(node) });
                      result.push({ type: 'plain', value: '\n' });
                    }
                    node.children.forEach(traverseNode);
                  }
                  traverseNode(subject);
                  body.push({ type: 'arr', value: result });
                  break;
                }

                case 'm:PrettyPrint': {
                  body.push({ type: 'stdout', value: 'Unsupported in this demo version of CodeProber' });
                  break;
                }

                default: {
                  // Normal probe
                  const res = subject.props[msg.property.name]?.();
                  if (res?.type && res?.start && res?.end) {
                    // Looks like a node
                    body.push({ type: 'node', value: nodeToLocator(res) });
                  } else {
                    // Treat like a string
                    body.push({ type: 'plain', value: JSON.stringify(res) ?? '' });
                  }
                  break;
                }
              }
            }
          }
          return {
            response: {
              type: 'sync',
              value: {
                body,
                totalTime: 0, parseTime: 0, createLocatorTime: 0, applyLocatorTime: 0, attrEvalTime: 0, listNodesTime: 0, listPropertiesTime: 0,
                locator: responseLocator,
                errors: errors.length ? errors : undefined,
              }
            },
          }
        }
      }
      return Promise.reject(`Unknown message type ${msg.type}`);
    },
    on: () => { }, // No callbacks
  }
  const styling = frame.contentWindow.document.head.appendChild(frame.contentWindow.document.createElement('style'));
  styling.innerText = `
  .cm-editor, html {
    font-size: 16px;
  }
  `;
  frame.contentWindow.initCodeProber();
}

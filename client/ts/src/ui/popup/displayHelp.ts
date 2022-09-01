import repositoryUrl from "../../model/repositoryUrl";
import createModalTitle from "../create/createModalTitle";
import showWindow from "../create/showWindow";

const createSyntaxNode = (type: string, text: string, margins?: string) => {
  const retNode = document.createElement('span');
  if (type) {
    retNode.classList.add(type);
  }
  if (margins?.includes('left')) retNode.style.marginLeft = '0.5rem';
  if (margins?.includes('right')) retNode.style.marginRight = '0.5rem';
  retNode.innerText = text;
  return retNode;
}

const getHelpTitle = (type: HelpType) => ({
  'general': 'How to use CodeProber 🔎',
  'recovery-strategy': 'Position recovery',
  'probe-window': 'Probe help',
  'magic-stdout-messages': 'Magic stdout messages',
  'ast-cache-strategy': 'AST caching',
  'syntax-highlighting': 'Syntax Highlighting',
  'main-args-override': 'Main args override',
  'customize-file-suffix': 'Temp file suffix',
  'property-list-usage': 'Property list help',
  'show-all-properties': 'Show all properties',
})[type];

const getHelpContents = (type: HelpType) => {
  const createHeader = (text: string) => {
    const header = document.createElement('span');
    header.classList.add('syntax-attr');
    header.innerText = text;
    return header;
  };
  const joinElements = (...parts: (string | HTMLElement)[]) => {
    const wrapper = document.createElement('div');
    parts.forEach(p => {
      if (typeof p === 'string') {
        wrapper.appendChild(document.createTextNode(p));
      } else {
        wrapper.appendChild(p);
      }
      return p;
    });
    return wrapper;
  }
  switch (type) {

    case 'general': {

      const exampleVisible = document.createElement('div');
      {
        const add = (...args: string[]) => exampleVisible.appendChild(createSyntaxNode(args[0], args[1], args[2]));
        exampleVisible.appendChild(document.createTextNode('Example (JastAdd syntax): '));
        add('syntax-modifier', 'syn', 'right');
        add('syntax-type', 'boolean List');
        add('syntax-attr', '.cpr_nodeListVisible', '');
        add('', '() =', 'right');
        add('syntax-modifier', 'false', 'false');
        add('', ';', '');
      }
      const exampleAttrs = document.createElement('div');
      {
        const add = (...args: string[]) => exampleAttrs.appendChild(createSyntaxNode(args[0], args[1], args[2]));
        exampleAttrs.appendChild(document.createTextNode('Example (JastAdd syntax): '));
        add('syntax-modifier', 'syn', 'right');
        add('syntax-type', 'java.util.List<String> Function');
        add('syntax-attr', '.cpr_propertyListShow', '');
        add('', '() =', 'right');
        add('syntax-type', 'Arrays.asList(', '');
        add('syntax-string', '"eval"', '');
        add('', ', ', '');
        add('syntax-string', '"reference"', '');
        add('', ');', '');
      }
      const exampleView = document.createElement('div');
      {
        const add = (...args: string[]) => exampleView.appendChild(createSyntaxNode(args[0], args[1], args[2]));
        exampleView.appendChild(document.createTextNode('Example (JastAdd syntax): '));
        add('syntax-modifier', 'syn', 'right');
        add('syntax-type', 'Object IntType');
        add('syntax-attr', '.cpr_getOutput', '');
        add('', '() =', 'right');
        add('syntax-string', '"int"');
        add('', ';', '');
      }

      const viewDefault = document.createElement('pre');
      viewDefault.style.marginTop = '2px';
      viewDefault.style.marginLeft = '2px';
      viewDefault.style.fontSize = '0.875rem';
      viewDefault.innerText = `
encode(value):
  if (value is ASTNode):
    if (value has 'cpr_getOutput'): encode(value.cpr_getOutput())
    else: output(value.location, value.type)

  if (value is Iterator or Iterable):
    for (entry in value): encode(entry)

  if no case above matched: output(value.toString())
`.trim();

      return [
        `Right click on some text in the editor and click 'Create Probe' to get started.`,
        `If you get the message 'No AST node at this location', then it likely means that something went wrong during parsing.`,
        `Look at the terminal where you started code-prober.jar for more information.`,
        ``,
        `There are a number of 'magic' attributes you can add to your AST nodes to modify their behavior in this tool.`,
        `All magic attributes are prefixed with 'cpr_' (CodePRober_) to avoid colliding with your own functionality.`,
        `There three main magic attributes you may want to add are:`,
        ``,
        joinElements(`1) '`, createHeader('cpr_nodeListVisible'), `'. This controls whether or not a node will appear in the 'Create Probe' node list.`),
        `Default: `,
        `-    false: for 'List' and 'Opt'. Note: this is only default, you can override it.`,
        `-     true: for all other types.`,
        exampleVisible,
        ``,
        joinElements(`2) '`, createHeader('cpr_propertyListShow'), `'. A collection (List<String> or String[]) that is used to include extra properties in the property list seen when creating probes.`),
        `Functions are shown in the property list if all of the following is true:`,
        `- The function is public.`,
        `- The argument types are 'String', 'int', 'boolean', or a subtype of the top AST Node type.`,
        `- One of the following is true:`,
        `-- The function is an attribute (originates from a jrag file, e.g 'z' in 'syn X Y.z() = ...)`,
        `-- The function is an AST child accessor (used to get members declared in an .ast file).`,
        `-- The function name is either 'toString', 'getChild', 'getNumChild' or 'getParent'`,
        `-- The function name is found in the return value from cpr_propertyListShow()`,
        `Default: empty array.`,
        exampleAttrs,
        ``,
        joinElements(`3) '`, createHeader('cpr_getOutput'), `'. This controls how a value is shown in the output (lower part) of a probe.`),
        `Default: encodes one or more options in order. In pseudocode:`,
        viewDefault,
        exampleView,
        ``,
        `The way this tool is built, it cannot help you find & fix infinite loops.`,
        joinElements(`For infinite loops we instead recommend you use other tools like `, (() => {
          const a = document.createElement('a');
          a.href = 'https://docs.oracle.com/javase/1.5.0/docs/tooldocs/share/jstack.html';
          a.innerText = 'jstack';
          a.target = '_blank';
          return a;
        })(), ' and/or traditional breakpoint/step-debuggers.'),
        ``,
        joinElements(`Contributions welcome at `, (() => {
          const a = document.createElement('a');
          a.href = repositoryUrl;
          a.innerText = repositoryUrl;
          a.target = '_blank';
          return a;
        })()),
      ];
    }

    case 'recovery-strategy': {
      const settingsExplanation = document.createElement('div');
      settingsExplanation.style.display = 'grid';
      settingsExplanation.style.gridTemplateColumns = 'auto auto 1fr';
      settingsExplanation.style.gridColumnGap = '0.5rem';
      [
        [`Fail`, `don\'t try to recover information`],
        [`Parent`, `search recursively upwards through parent nodes, using the equivalent of 'node.getParent()'`],
        [`Child`, `search recursively downwards through child nodes, using the equivalent of 'node.getChild(0)'`],
        [`Parent->Child`, `Try 'Parent'. If no position is found, try 'Child'.`],
        [`Child->Parent`, `Try 'Child'. If no position is found, try 'Parent'.`],
        [`Zigzag`, `Similar to 'Parent->Child', but only search one step in one direction, then try the other direction, then another step in the first direction, etc. Initially searches one step upwards.`],
      ].forEach(([head, tail]) => {
        const headNode = document.createElement('span');
        headNode.style.textAlign = 'right';
        headNode.classList.add('syntax-attr');
        headNode.innerText = head;
        settingsExplanation.appendChild(headNode);

        settingsExplanation.appendChild(document.createTextNode('-'));

        const tailNode = document.createElement('span');
        tailNode.innerText = tail;
        settingsExplanation.appendChild(tailNode);
      })
      return [
        'Some nodes in your AST might be missing location information',
        'This editor is built around the idea that all AST nodes have positions, and the experience is worsened for nodes where this isn\'t true.',
        '',
        'There are two solutions',
        '',
        '1) Fix your parser',
        'Usually position information is missing because of how you structured your parser.',
        'Maybe you do some sort of desugaring in the parser, and create multiple AST nodes in a single production rule.',
        'Beaver, for example, will only give a single node position information per production rule, so try to ony create a single node per rule.',
        `Note that this isn't a solution for nodes generated by NTA's. They will need to use solution 2.`,
        '',
        '2) Use a recovery strategy',
        'If a node is missing location information, then we can sometimes get it from nearby nodes.',
        'This setting controls just how we search for information. Which option fits best for you depends on how you built your AST.',
        'Settings:',
        settingsExplanation,
        '',
        `No strategy guarantees success. If position is missing, it will be marked with '⚠️', and you\'ll likely run into problems when using it`,
        `If you are unsure of what to use, 'Zigzag' is usually a pretty good option.`,
      ];
    }

    case 'probe-window': {
      return [
        `This window represents an active 'probe'`,
        `The titlebar shows the input to the probe; namely node type and attribute name.`,
        `Sometimes you'll also see attribute arguments here, and a pen that lets you edit them.`,
        `Finally, the title bar show where the probed node exists. You can hover this to highlight the node in the document.`,
        '',
        'Below the titlebar is the output of the probe.',
        `This is the resolved value of the probe, formatted according to the 'cpr_getOutput' logic (see general help window for more on this).`,
        '',
        `If you check 'Capture stdio' on the top right, you'll also see any messages printed to System.out and System.err in the window.`,
        `If the cache strategy is set to 'None' or 'Purge', then each probe will get evaluated in a fresh compiler instance in isolation, so any values and messages you see in the probe window belongs only to that window.`,
        '',
        'The probes are automatically reevaluated whenever the document changes.',
        `The probes also automatically update when the underlying jar file (usually 'compiler.jar') changes.`,
        `Therefore you can use probes as a sort of automatic test case runner. Write some code, open some probes, then move it to a secondary monitor and continue working on your compiler.`,
        `Whenever you rebuild your compiler, glance at your probes. They should now display fresh values.`,
      ];
    }

      case 'magic-stdout-messages': {
        const createParent = () => {
          const parent = document.createElement('div');
          parent.style.display = 'grid';
          parent.style.gridTemplateColumns = 'auto 1fr';
          parent.style.rowGap = '0.125rem';
          parent.style.columnGap = '0.5rem';
          return parent;
        }
        let entryParent: HTMLElement;
        const createEntry = (pattern: string, explanation: string) => {
          const patternHolder = document.createElement('span');
          patternHolder.classList.add('syntax-string');
          patternHolder.style.textAlign = 'right';
          patternHolder.innerText = pattern;

          const explanationHolder = document.createElement('span');
          explanationHolder.innerText = explanation;

          entryParent.appendChild(patternHolder);
          entryParent.appendChild(explanationHolder);
        };

        // TODO rename the patterns a bit? Prefix everything by PASTA- perhaps?
        const patternsParent = entryParent = createParent();
        createEntry('ERR@S;E;MSG', 'Show a red squiggly line.');
        createEntry('WARN@S;E;MSG', 'Show a yellow squiggly line.');
        createEntry('INFO@S;E;MSG', 'Show a blue squiggly line.');
        createEntry('LINE-PP@S;E;COL', 'Draw a plain line.');
        createEntry('LINE-PA@S;E;COL', 'Draw line that starts plain and ends with an arrow.');
        createEntry('LINE-AP@S;E;COL', 'Draw line that starts with an arrow and ends plain.');
        createEntry('LINE-AA@S;E;COL', 'Draw line with arrows on both ends.');

        const patternsExamples = entryParent = createParent();
        createEntry('ERR@40964;40966;Hello', `Red squiggly line on line 10, column 4 to 6. Shows 'Hello' when you hover over it`);
        createEntry('INFO@16384;32767;Hi', `Blue squiggly line on the entirety of lines 4, 5, 6 and 7. Shows 'Hi' when you hover over it`);
        createEntry('LINE-PA@4096;20490;#0FFF', `Solid cyan line from start of line 1 to line 5, column 10. Has arrow on the end.`);
        createEntry('LINE-AA@16388;16396;#0F07', `Semi-transparent green double-sided arrow on line of line 4 from column 4 to 12`);

        const sampleAttr = document.createElement('pre');
        sampleAttr.style.marginTop = '6px';
        sampleAttr.style.marginLeft = '2px';
        sampleAttr.style.fontSize = '0.875rem';
        sampleAttr.innerText = `
aspect MagicOutputDemo {
  void ASTNode.outputMagic(String type, String arg) {
    System.out.println(type + "@" + getStart() + ";" + getEnd() + ";" + arg);
  }
  coll HashSet<ASTNode> Program.thingsToHighlightBlue() root Program;
  MyNodeType contributes this
    to Program.thingsToHighlightBlue()
    for program();

  syn Object Program.drawBlueSquigglys() {
    for (ASTNode node : thingsToHighlightBlue()) {
      node.outputMagic("INFO", "This thing is highlighted because [..]");
    }
    return null;
  }
}
`.trim();
        const copyButton = document.createElement('button');
        copyButton.innerText = 'Copy to clipboard';
        copyButton.onclick = () => {
          navigator.clipboard.writeText(sampleAttr.innerText);
        }
        return [
          `There are a number of 'magic' messages you can print to System.out.`,
          `Whenever probes are evaluated, these messages are intercepted (even if 'Capture stdio' isn't checked!).`,
          `The patterns and effects of the magic messages are shown below:`,
          '',
          patternsParent,
          '',
          `'S' and 'E' stand for 'start' and 'end', and are ints containing line and column. 20 bits for line, 12 for column, e.g: 0xLLLLLCCC.`,
          'Example: 20493 represents line 5 and column 13 (20493 = (5 << 12) + 13).',
          '',
          `'MSG' is any string. This string is displayed when you hover over the squiggly lines`,
          '',
          `'COL' is a hex-encoded color in the form #RGBA.`,
          'Example: #F007 (semi-transparent red)',
          '',
          'Some example messages and their effects are listed below:',
          patternsExamples,
          '',
          `The arrows don't work (or only partially work) for lines that are connected to offscreen or invalid positions.`,
          'For example, if you try to draw a line with one end at line 2, column 5, but that line only has 3 characters, then the line will instead point at column 3.',
          '',
          `These special messages can be used as a some custom styling/renderer to help understand how your compiler works.`,
          `The following code can be used as a starting point`,
          sampleAttr,
          copyButton,
          `Once you have the code in an aspect and have recompiled, open a probe for the attribute 'drawBlueSquigglys' to see all instances of 'MyNodeType' have blue lines under them.`,
          'Note that the squiggly lines (and all other arrows/lines) only remain as long as their related probe window remains open.'
        ];
      }

      case "ast-cache-strategy": {
        const settingsExplanation = document.createElement('div');
        settingsExplanation.style.display = 'grid';
        settingsExplanation.style.gridTemplateColumns = 'auto auto 1fr';
        settingsExplanation.style.gridColumnGap = '0.5rem';
        [
          [`Full`, `Cache everything`],
          [`Partial`, `Cache the AST, but call 'flushTreeCache' on the root before evaluating any probe. This ensures that cached attributes are invoked for every probe.`],
          [`None`, `Don't cache the AST.`],
          [`Purge`, `Don't cache the AST or even the underlying jar file, fully reload from the file system each time. This resets all global state, but kills the JVMs ability to optimize your code. This is terrible for performance.`],
        ].forEach(([head, tail]) => {
          const headNode = document.createElement('span');
          headNode.style.textAlign = 'right';
          headNode.classList.add('syntax-attr');
          headNode.innerText = head;
          settingsExplanation.appendChild(headNode);

          settingsExplanation.appendChild(document.createTextNode('-'));

          const tailNode = document.createElement('span');
          tailNode.innerText = tail;
          settingsExplanation.appendChild(tailNode);
        })


        return [
          `When multiple probes are active, the same editor state will be evaluated multiple times (once for each probe).`,
          `When this happens, we can re-use the AST multiple to avoid unnecessary re-parses. There are however reasons that you might not want to re-use the AST, or at least not fully.`,
          '',
          `While it is technically bad practice, you can use "printf-style" debugging in your attributes (System.out.println(..)).`,
          `Cached attributes will only output such printf-messages once. With multiple active probes, this makes it uncertain which probe will capture the message.`,
          `Even worse, if you have any form of mutable state in your AST (please don't!), then reusing an AST can cause unpredictable behavior when parsing.`,
          `There are a few strategies you can use:`,
          '',
          settingsExplanation,
          '',
          `Performance is best with 'Full', and worst with 'Purge'.`,
          `"Debuggability" is best with 'Purge', and worst with 'Full'.`,
          `If you are unsure of what to use, 'Partial' is usually a pretty good option.`,
        ];
      }

      case 'syntax-highlighting': return [
        `This setting controls which style of highlighting is used in the editor.`,
        `This also affects the suffix used for temporary files, unless 'Custom file suffix' is checked.`,
      ];

      case 'main-args-override': return [
        `When your underlying tool is invoked, the path to a temporary file is sent as an arg to the main method.`,
        `Optionally, some extra args are also included.`,
        `By default, the extra args are defined when you start the CodeProber server.`,
        `For example, running 'java -jar code-prober-jar path/to/your/tool.jar foo bar baz', will set the extra args array to [foo, bar, baz].`,
        `By checking 'Override main args' and clicking "Edit", you can override those extra args.`,
        ``,
        `Args are separated by spaces and/or newlines.`,
        `To include a space in an arg, wrap the arg in quotes (e.g "foo bar").`,
        `To include a newline, quote or backslash in an arg, prefix the char with \\ (e.g \\n, \\" and \\\\).`,
      ];

      case 'customize-file-suffix': return [
        `By default, the editor state is written to a temporary file with a file suffix that matches the chosen syntax highlighting.`,
        `For example, if the highlighting is set to 'Python', then the temp file will end with '.py'.`,
        ``,
        `If you work on a language not represented in the syntax highlighting list, then this might result in your compiler/analyzer rejecting the temporary file due to it having an unknown suffix.`,
        `By checking 'Custom file suffix' you can change the default suffix to something else.`,
        `Note that custom suffixes are used as-is. If you want temp files to end with '.txt', then you must set the custom suffix to exactly '.txt' (including the dot).`,
      ];

      case 'property-list-usage': return [
        `This is the list of available properties on the node you selected.`,
        `The list is filtered according to the 'cpr_propertyListShow' logic (see general help window for more on this).`,
        `When no filter is added, the properties are sorted by two criteria in order:`,
        `1) Properties representing AST child accessors. This corresponds to field declarations in ast files. If you write 'MyNode ::= MyChild:TheType;', then the property 'getMyChild()' will appear high up in this list.`,
        `2) Alphabetical ordering.`,
        ``,
        `When a filter is added, this list is instead is sorted by:`,
        `1) Properties that match the filter. The filter is case insensitive and allows arbitrary characters to appear in between the filter characters. For example, 'gl' matches 'getLorem' but not 'getIpsum'.`,
        `2) Alphabetical ordering`,
      ];

      case 'show-all-properties': return [
        `By default, the property list shown while creating a probe is filtered according to the 'cpr_propertyListShow' logic (see general help window for more on this).`,
        `The last criteria of that filter is that the function must follow one of a few predicates to be shown.`,
        `This checkbox basically adds a '|| true' to the end of that predicate list. I.e any function that is public and has serializable argument types will be shown.`,
        `There can potentially be a very large amount of functions shown is you check this box, which can be annoying.`,
        `In addition, some of the non-standard functions might cause mutations (like 'setChild(int, ..)'), which can cause undefined behavior when used in this tool.`,
        `In general, we recommend you keep this box unchecked, and only occasionally re-check it.`,
      ]
  }
}

const displayHelp = (type: HelpType, setHelpButtonDisabled: (disabled: boolean) => void): void => {
  setHelpButtonDisabled(true);
  // TODO prevent this if help window already open
  // Maybe disable the help button, re-enable on close?
  const helpWindow = showWindow({
    rootStyle: `
      width: 32rem;
      min-height: 8rem;
    `,
    resizable: true,
    render: (root) => {
      root.appendChild(createModalTitle({
        renderLeft: (container) => {
          const header = document.createElement('span');
          header.innerText = getHelpTitle(type);
          container.appendChild(header);
        },
        onClose: () => {
          setHelpButtonDisabled(false);
          helpWindow.remove();
        },
      }).element);

      const textHolder = document.createElement('div');
      textHolder.style.padding = '0.5rem';

      const paragraphs = getHelpContents(type);
      paragraphs.forEach(p => {
        if (!p) {
          textHolder.appendChild(document.createElement('br'));
          return;
        }
        if (typeof p !== 'string') {
          textHolder.appendChild(p);
          return;
        }
        const node = document.createElement('p');
        // node.style.whiteSpace = 'pre';
        // node.style.maxWidth = '31rem';
        // const leadingWhitespace = p.length - p.trimStart().length;
        // if (leadingWhitespace) {
        //   let ws = '';
        //   for (let i = 0; i < leadingWhitespace; i++) {
        //     ws = ws + ' ';
        //   }
        //   const wsnode = document.createElement('span');
        //   wsnode.style.whiteSpace = 'pre';
        //   wsnode.innerText = ws;
        //   node.appendChild(wsnode);
        // }
        node.appendChild(document.createTextNode(p));
        node.style.marginTop = '0';
        node.style.marginBottom = '0';
        textHolder.appendChild(node);
      });

      root.appendChild(textHolder);
    }
  });
}

export default displayHelp;

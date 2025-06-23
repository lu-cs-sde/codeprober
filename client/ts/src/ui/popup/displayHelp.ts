import { repositoryUrl } from "../../model/repositoryUrl";
import createModalTitle from "../create/createModalTitle";
import createTextSpanIndicator, { TextSpanStyle } from "../create/createTextSpanIndicator";
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
  'group-properties-by-aspect': 'Group properties',
  'duplicate-probe-on-attr': 'Duplicate probe',
  'capture-stdout': 'Capture stdout',
  'capture-traces': 'Capture traces',
  'location-style': 'Location styles',
  'textprobe-style': 'TextProbe styles',
  'ast': 'AST',
  'test-code-vs-codeprober-code': 'Test code vs CodeProber code',
  'auto-shorten-property-names': 'Auto Shorten Property Names',
})[type];

const getHelpContents = (type: HelpType): (string|HTMLElement)[] => {
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
        `If you get the message 'Node listing failed', then it likely means that something went wrong during parsing.`,
        `Look at the terminal where you started codeprober.jar for more information.`,
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
        `- The argument types are 'String', 'int', 'boolean', 'java.io.OutputStream', 'java.io.PrintStream' or a subtype of the top AST Node type.`,
        `- One of the following is true:`,
        `-- The function is an attribute (originates from a jrag file, e.g 'z' in 'syn X Y.z() = ...')`,
        `-- The function is an AST child accessor (used to get members declared in an .ast file).`,
        `-- The function name is either 'toString', 'getChild', 'getNumChild', 'getParent' or 'dumpTree'`,
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
      });


      const sampleAttr = document.createElement('pre');
      sampleAttr.style.marginTop = '6px';
      sampleAttr.style.marginLeft = '2px';
      sampleAttr.style.fontSize = '0.875rem';
      sampleAttr.innerText = `
aspect DumpSubtree {

  // Bypass/inline Opt/List
  void ASTNode.bypassDumpSubtree(java.util.List<Object> dst, int budget) {
    if (budget <= 0) return;
    for (ASTNode child : astChildren()) child.dumpSubtree(dst, budget);
  }
  void Opt.dumpSubtree(java.util.List<Object> dst, int budget) { bypassDumpSubtree(dst, budget); }
  void List.dumpSubtree(java.util.List<Object> dst, int budget) { bypassDumpSubtree(dst, budget); }

  void ASTNode.dumpSubtree(java.util.List<Object> dst, int budget) {
    dst.add(this);
    if (getNumChild() == 0 || budget <= 0) { return; }

    final java.util.List<Object> ret = new java.util.ArrayList<>();
    for (ASTNode child : astChildren()) child.dumpSubtree(ret, budget - 1);
    dst.add(ret);
  }

  syn Object ASTNode.dumpSubtree(int budget) {
    java.util.List<Object> dst = new java.util.ArrayList<>();
    for (ASTNode child : astChildren()) child.dumpSubtree(dst, budget);
    if (dst.size() == 1 && dst.get(0) instanceof java.util.List<?>) {
      return dst.get(0);
    }
    return dst;
  }
  syn Object ASTNode.dumpSubtree() { return dumpSubtree(999); }

}
`.trim();
      const copyButton = document.createElement('button');
      copyButton.innerText = 'Copy to clipboard';
      copyButton.onclick = () => {
        navigator.clipboard.writeText(sampleAttr.innerText);
      }
      return [
        'Some nodes in your AST might be missing location information.',
        'CodeProber is heavily built around the idea that all AST nodes have positions, and the experience is worsened for nodes where this isn\'t true.',
        '',
        'There are two solutions:',
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
        `An efficient way to root out parser problems is to pick 'Fail', then dump the entire tree using the following 'dumpSubtree' attribute (JastAdd syntax):`,
        sampleAttr,
        copyButton,
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
        `If you check 'Capture stdout' on the top right, you'll also see any messages printed to System.out and System.err in the window.`,
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
          `Whenever probes are evaluated, these messages are intercepted (even if 'Capture stdout' isn't checked!).`,
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
          `When this happens, we can re-use the AST multiple times to avoid unnecessary re-parses. There are however reasons that you might not want to re-use the AST, or at least not fully.`,
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
        `For example, running 'java -jar codeprober.jar path/to/your/tool.jar foo bar baz', will set the extra args array to [foo, bar, baz].`,
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
        `There is potentially a very large amount of functions shown is you check this box, which can be annoying.`,
        `In addition, some of the non-standard functions might cause mutations (like 'setChild(int, ..)'), which can cause undefined behavior when used in this tool.`,
        `In general, we recommend you keep this box unchecked, and only occasionally re-check it.`,
      ];

      case 'group-properties-by-aspect': return [
        `Check this to group and filter property names by their containing aspect file.`,
        `This affects the dialog where you select a property, i.e after you've selected an AST node.`,
        ``,
        `This is only applicable for JastAdd tools.`
      ];

      case 'duplicate-probe-on-attr': return [
        `When you have created a probe, you can click the property name to create a new probe on the same node, but with a different property.`,
        `This click can either create a new probe window, or replace the old one.`,
        `If this box is checked, then a new window will be created.`,
        `If this box is unchecked, then it will replace the old window.`,
        `By holding 'Shift' while clicking the property name, you can access the 'reverse' functionality.`,
        `I.e if the box is checked and you hold shift, then the window will be replaced, and vice versa.`,
      ];

      case 'capture-stdout': {
        const styled = (text:string, cls: string) => {
          const span = document.createElement('span');
          span.classList.add(cls);
          span.innerText = text;
          return span;
        };
        return [
          `Check this if you want messages to stdout and stderr to be shown in probe outputs.`,
          `'printf-debugging' should generally be avoided if possible, but if you feel it is strictly needed then you can use this checkbox to access it.`,
          joinElements(`Captured messages are displayed with a `, styled('blue', 'captured-stdout'), ` color if they were printed to stdout, and a `, styled('red', 'captured-stderr'), ` color if they were printed to stderr.`),
          ``,
          `Note that only messages printed during property evaluation are captured.`,
          `Messages printed during parsing are not shown here, but can still be seen in the terminal where you started codeprober.jar.`,
          `An exception to this is when parsing fails, in which case messages during parsing are displayed (even if this checkbox is unchecked).`,
        ];
      }

      case 'capture-traces': {
        const code = document.createElement('pre');
        code.innerText = '  ' + [
          `public void Program.cpr_setTraceReceiver(final java.util.function.Consumer<Object[]> recv) {`,
          `  trace().setReceiver(new ASTState.Trace.Receiver() {`,
          `    @Override`,
          `    public void accept(ASTState.Trace.Event event, ASTNode node, String attribute, Object params, Object value) {`,
          `      recv.accept(new Object[] { event, node, attribute, params, value });`,
          `    }`,
          `  });`,
          `}`,
        ].join('\n  ');
        const copyButton = document.createElement('button');
        copyButton.innerText = 'Copy to clipboard';
        copyButton.onclick = () => {
          navigator.clipboard.writeText(code.innerText);
        }
        return [
          `Check this if you want to capture traces of indirect dependencies while evaluating properties.`,
          `Once checked, you can optionally also decide if you want to call flushTreeCache() before each time traces are collected.`,
          `If you perform computations during main() that results in cached values in your AST, then you wouldn't see the traces of those computations when the probe is evaluated.`,
          `By always performing an extra flushTreeCache() prior to collecting traces, we get a bigger and more accurate trace, at the cost of some speed.`,
          ``,
          `Tracing is an advanced feature which requires some customization in your tool to be able to use.`,
          `If you are using JastAdd, add a --tracing flag in your build script, and then the following aspect code (replace 'Program' with your root node type):`,
          code,
          copyButton
        ];
      }

      case 'location-style': {
        const sp: Span = { lineStart: 1, colStart: 2, lineEnd: 3, colEnd: 4 };
        const createExplanationPanel = (entries: [string, TextSpanStyle, Span][]) => {
          const settingsExplanation = document.createElement('div');
          settingsExplanation.style.paddingLeft = '1rem';
          settingsExplanation.style.display = 'grid';
          settingsExplanation.style.gridTemplateColumns = 'auto auto 1fr';
          settingsExplanation.style.gridColumnGap = '0.5rem';
          entries.forEach(([head, tail, span]) => {
            const headNode = document.createElement('span');
            headNode.style.textAlign = 'right';
            headNode.classList.add('syntax-attr');
            headNode.innerText = head;
            settingsExplanation.appendChild(headNode);

            settingsExplanation.appendChild(document.createTextNode('-'));

            settingsExplanation.appendChild(createTextSpanIndicator({
              span,
              styleOverride: tail,
            }))
            // const tailNode = document.createElement('span');
            // tailNode.innerText = tail;
            // settingsExplanation.appendChild(tailNode);
          });
          return settingsExplanation;
        }

        return [
          `In several locations in CodeProber you can see location indicators.`,
          `This setting control how the location indicators are presented. Example values can be seen below for a location that starts at line 1, column 2 and ends at line 3, column 4.`,
          ``,
          createExplanationPanel([
            [`Full`, 'full', sp],
            [`Lines`, 'lines', sp],
            [`Start`, 'start', sp],
            [`Start line`, `start-line`, sp],
          ]),
          ``,
          `The 'compact' options look like the non-compact options if the start and end lines are different. If start and end lines are equal, then it looks like this:`,
          createExplanationPanel([
            [`Full compact`, 'full-compact', { ...sp, lineEnd: 1 }],
            [`Lines compact`, 'lines-compact', { ...sp, lineEnd: 1 }],
          ]),
          ``,
          `Note that this setting doesn't affect the hover highlighting. The exact line/column is highlighted, even if the indicator only shows the start line for example.`,
        ];
      }

      case 'textprobe-style': {
        type PatternEntry = string | [string, string];
        const parted = (parts: PatternEntry[]) => {
          const ret = document.createElement('div');
          const addPart = (text: string, clazz: string = '') => {
            const part = document.createElement('span');
            part.innerText = text;
            if (clazz) {
              if (clazz.trim()) {
                part.classList.add(clazz);
              }
              if (clazz === ' ' || clazz.startsWith('syntax-')) {
                part.style.fontFamily = 'monospace';
              }
            }
            ret.appendChild(part);
          };

          parts.forEach(p => typeof p === 'string' ? addPart(p) : addPart(p[0], p[1]));
          return ret;
        }
        const ol = (items: (string | HTMLElement)[]) => {
          const ret = document.createElement('ol');
          items.forEach(itm => {
            const li = document.createElement('li');
            if (typeof itm === 'string') {
              li.innerText = itm;
            } else {
              li.appendChild(itm);
            }
            ret.appendChild(li);
          });
          return ret;
        }

        const patternExpl = parted([
          '[[',
          ['A', 'syntax-type'],
          '[',
          ['i', 'syntax-int'],
          '].',
          ['b', 'syntax-attr'],
          '=',
          ['c', 'syntax-string'],
          ']]',
        ]);
        patternExpl.style.padding = '0.25rem';
        patternExpl.style.margin = '0.25rem 0 0.25rem 2rem';
        patternExpl.style.outline = '1px dashed gray';
        patternExpl.style.fontFamily = 'monospace';
        patternExpl.style.display = 'inline-block';

        const example = (pattern: string, explanation: string) => {
          const ret = document.createElement('div');
          ret.style.marginLeft = '2rem';
          ret.style.marginBottom = '0.25rem';

          const patternNode = parted([pattern, ' ']);
          // patternNode.style.display = 'inline';
          ret.appendChild(patternNode);

          const explNode = parted([[explanation, 'stream-arg-msg']]);
          // explNode.style.display = 'inline';
          explNode.style.fontStyle = 'italic';
          ret.appendChild(explNode);

          return ret;
        }
        return [
          `CodeProber supports probes that exist purely in textual form inside the text editor. These 'text probes' are specified with the follwing pattern by default:`,
          patternExpl,
          `The above pattern tells CodeProber to do the following:`,
          ol([
            parted([
              `Find all nodes on the line where the text probe is specified that are of (sub-)type `,
              ['A', 'syntax-type'],
              `, using a left-to-right depth-first search.`,
            ]),
            parted([
              `Select the `,
              ['i', 'syntax-int'],
              `:th node in the resulting list.`,
            ]),
            parted([
              `Evaluate `,
              ['b', 'syntax-attr'],
              ` on the node.`,
            ]),
            parted([
              `Compare the result of `,
              ['b', 'syntax-attr'],
              ` with `,
              ['c', 'syntax-string'],
              `.`,
            ])
          ]),
          parted([
            '"',
            ['[', ' '],
            ['i', 'syntax-int'],
            [']', ' '],
            '"  is optional if there is only one node on the given line that matches (sub-)type ',
            ['A', 'syntax-type'],
            '.',
          ]),
          parted([
            '"',
            ['.', ' '],
            ['b', 'syntax-attr'],
            `" can be a list of multiple properties, like "`,
            ['.', ' '], ['b', 'syntax-attr'],
            ['.', ' '], ['x', 'syntax-attr'],
            ['.', ' '], ['y', 'syntax-attr'],
            ['.', ' '], ['z', 'syntax-attr'],
            `". All non-last properties must resolve to an AST node reference, similar to nested probes.`,
          ]),
          parted([
            '"',
            ['=', ' '],
            ['c', 'syntax-string'],
            '" is optional.',
          ]),
          ``,
          parted([
            `If a comparison is included ("`,
            ['=', ' '],
            ['c', 'syntax-string'],
            `"), then the text probe is highlighted `,
            ['green', 'elp-result-success'],
            ` or `,
            ['red', 'elp-result-fail'],
            ` depending on if the comparison succeeded or not.`
          ]),
          `Comparisons can include '!' and/or '~' before the equals sign. Adding '!' means 'not', i.e. invert the comparison. '~' means 'contains', i.e. do a substring comparison.`,
          ``,
          `Some example text probes and their possible meanings are listed below`,
          ``,
          example('[[CallExpr.type=int]]', 'The function call on this line has type int.'),
          example('[[Program.errors=[]]]', 'There are no errors in this program.'),
          example('[[Program.errors~=duplicate definition]]', 'There is at least one error containing the message "duplicate definition"'),
          example('[[IfStmt.getCond.expectedType=bool]]', 'The expected type of the if-condition is boolean'),
          example('[[Expr[2].prettyPrint=abc]]', 'Pretty-printing the third Expr on this line results in "abc"'),
          ``,
          `The exact meaning of text probes depend on the underlying tool (compiler/analyzer) being explored in CodeProber, just as with normal probes.`,
          ``,
          `CodeProber has no knowledge of the syntax of the underlying tool, therefore it will search for text probes everywhere, including possibly in normal program code. In some languages, text like "[[A.b]]" is a valid expression that you may want to write.`,
          `When something is intended to be interpreted as a text probe, consider putting them  in a comment (e.g. prefix with "//", "#" or similar).`,
          `When something is intended to be an expression in the language, you have two main options:`,
          ol([
            `Add whitespace somewhere. For example, "[[ A.b ]]" is not interpreted as a text probe`,
            `Disable text probe support entirely by changing "TextProbe style" to "Disabled" in the settings panel`,
          ]),
        ]
      }

      case 'ast': {
        return [
          `This window displays the abstract syntax tree (AST) around a node in the tree.`,
          `Nodes can be hovered and interacted with, just like when the output of a normal probe is an AST node.`,
          `When you see '᠁', the AST has been truncated due to performance reasons.`,
          `You can click the '᠁' to continue exploring the AST from that point`,
        ];
      }
      case 'test-code-vs-codeprober-code': {
        return [
          `When a test is created it saves the current state of CodeProber. This includes the code in the main CodeProber text editor, as well as some of the settings (cache settings, main args, file suffix, etc.).`,
          ``,
          `When tests are executed, they do so with their saved state, *not* the current CodeProber state. This lets you have multiple tests at the same time, each with their on unique configuration.`,
          ``,
          `When a test fails, you may want to open probes to inspect why. The first step is to change the current CodeProber code to the test code. Open the test in question and click the 'Source Code' tab. There will be a button labeled 'Load Source' or 'Open Probe'.`,
          `• Clicking 'Load Source' will replace the code inside the main CodeProber editor with the saved code from the test.`,
          `• Clicking 'Open Probe' will open the probe corresponding to the test.`,
          `'Open Probe' is only available if the CodeProber code matches the test code.`,
        ];
      }
      case 'auto-shorten-property-names': {
        const reg = document.createElement('div');
        reg.innerText = `/^.*?([\\w\\d\\$_]+)$/`;
        reg.style.marginLeft = '1rem';
        return [
          `CodeProber supports multiple styles of language implementations, which in turn have different ways to identify properties. Implementations that are built using JastAdd use Java method names as identifiers for properties. Other implementations may rely on "labelled" properties instead, and those labels are any arbitrary strings. Such strings may be very long, and not necessarily user friendly.`,
          ``,
          `If this box is selected then CodeProber will try to automatically shorten property names. More specifically, it shows group 1 in the regex:`,
          ``,
          reg,
          ``,
          `If more than one property would shorten to the same value in a list of properties, then the full name is shown instead.`,
        ]
      }
  }
}

const displayHelp = (type: HelpType, setHelpButtonDisabled?: (disabled: boolean) => void): void => {
  setHelpButtonDisabled?.(true);

  const cleanup = () => {
    helpWindow.remove();
    setHelpButtonDisabled?.(false);
  };
  const helpWindow = showWindow({
    rootStyle: `
      width: 32rem;
      min-height: 8rem;
    `,
    onForceClose: cleanup,
    resizable: true,
    render: (root) => {
      root.appendChild(createModalTitle({
        renderLeft: (container) => {
          const header = document.createElement('span');
          header.innerText = getHelpTitle(type);
          container.appendChild(header);
        },
        onClose: cleanup,
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

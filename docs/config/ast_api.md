There are a number of methods you can implement on your AST nodes to affect how CodeProber interacts with your tool. All APIs should be public Java methods, which CodeProber later accesses via reflection. Some APIs are mandatory, others are optional.

# Mandatory API
A small number of methods **must** be set on the AST nodes, including:

| Key           | Type     | Description                                                         |
| ------------- | -------- | ------------------------------------------------------------------- |
| getParent     | AST Node | Get the parent for the given node, or `null` if it is the root node |
| getChild(int) | AST node | Get a child by index                                                |

In addition, CodeProber requires one of 5 variants of line/column and child counting methods. It checks for the presence of these accessors in numerical order (Variant 1, Variant 2, ...).
CodeProber has supports for these multiple variants in order to maximize compatibility with different styles of language implementation. If your are able to chose which variant to support, then variant 1 is preferred.

All line/columns values are expected to start at 1. For example, the top-left corner of a text editor is line 1 column 1. The leftmost column of the second line is line 2 column 1, etc.
If you do not have location data for a given node, return `0` in both line and column. Certain features in CodeProber, such as the AST node location tracking, treats line 0 column 0 specially.

## Variant 1

| Key                | Type | Description                 |
| ------------------ | ---- | --------------------------- |
| cpr_getStartLine   | int  | Starting line of the node   |
| cpr_getStartColumn | int  | Starting column of the node |
| cpr_getEndLine     | int  | Ending line of the node     |
| cpr_getEndColumn   | int  | Ending column of the node   |
| getNumChild        | int  | Get number of children      |

## Variant 2

| Key            | Type     | Description                 |
| -------------- | -------- | --------------------------- |
| getStartLine   | int      | Starting line of the node   |
| getStartColumn | int      | Starting column of the node |
| getEndLine     | int      | Ending line of the node     |
| getEndColumn   | int      | Ending column of the node   |
| getNumChild    | int      | Get number of children      |

## Variant 3
| Key            | Type     | Description                 |
| -------------- | -------- | --------------------------- |
| getBeginLine   | int      | Starting line of the node   |
| getBeginColumn | int      | Starting column of the node |
| getEndLine     | int      | Ending line of the node     |
| getEndColumn   | int      | Ending column of the node   |
| getNumChildren | int      | Get number of children      |

## Variant 4

| Key         | Type | Description                                                                                                            |
| ----------- | ---- | ---------------------------------------------------------------------------------------------------------------------- |
| getStart    | int  | Starting line and column of the node, packed as 0xLLLLLCCC. For example, line 5 column 7 is `20487` (`(5 << 12) + 7`). |
| getEnd      | int  | Ending line and column of the node, also packed as 0xLLLLLCCC.                                                         |
| getNumChild | int  | Get number of children                                                                                                 |

## Variant 5
This variant automatically assigns line 0 and column 0 to all nodes. This does not provide a good user experience, only use this if absolutely necessary.

| Key         | Type | Description            |
| ----------- | ---- | ---------------------- |
| getNumChild | int  | Get number of children |


# Optional API
The following table lists entirely optional APIs.
If you are using an API that is not listed here, then it may be deprecated and scheduled for removal. Please open an issue/pull request if you believe the undocumented API you use should be officially maintained.

There are different places where APIs are expected to appear, which are designated by the `Where` column in the table.
Possible places are:

- "ast" - any AST node
- "root" - the root of the AST
- "any" - any datastructure at all. Used when processing results from property evaluation.

| Key                            | Type                         | Default Value | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  | Where |
| ------------------------------ | ---------------------------- | ------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----- |
| cpr_isTALRoot                  | boolean                      | false         | If true, avoid trying to create `TAL` locators (see Section 4 in [paper](https://doi.org/10.1016/j.jss.2024.111980)) above this node in the AST. This should generally be set to true on a file/compilationUnit level. If implemented at the correct level, you will notice better CodeProber performance and tracking accuracy.                                                                                                                                                                                                                                                                                                                                                                                                                                             | ast   |
| cpr_nodeListVisible            | boolean                      | true          | If true, show this node in the list of nodes when you right click to create a new probe.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | ast   |
| cpr_propSearchVisible          | boolean                      | true          | If true, show this node in the results of search probes.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | ast   |
| cpr_cutoffNodeListTree         | boolean                      | false         | If true, avoid showing the subtree starting at this node in the list of nodes when you right click to create a new probe. You likely want to set this to `true` for file/compilationUnit nodes that are not the CodeProber input file.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       | ast   |
| cpr_isInsideExternalFile       | boolean                      | false         | If `true` the AST node appears in a part of the AST that was **not** parsed from the CodeProber file. This adds a small indicator in the UI to show that it is external, and improves node tracking for these nodes, since they are not expected to move around when the user makes changes in the CodeProber editor.                                                                                                                                                                                                                                                                                                                                                                                                                                                        | ast   |
| cpr_nodeLabel                  | String                       | null          | A label that is shown in the UI rather than the actual Java class of the node. Highly related to `cpr.type_identification_style` in [system_properties](system_properties.md).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | ast   |
| cpr_astLabel                   | String                       | null          | A label to show in the AST view in relation to the node. Can be used to for example show the value of an variable reference or literal. Do not place very long strings here, it will make the AST view quite cramped.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        | ast   |
| cpr_propertyListShow           | `String[]` or `List<String>` | null          | An list of property names to add in the list of properties when creating a new probe. By default, CodeProber will show anything annotated with an `ASTNodeAnnotation.Attribute` annotation, as is the case for JastAdd. With `cpr_propertyListShow` you can name properties that aren't annotated with `@Attribute` but that you still want to show in the list. The names can also be "labelled properties", which are arbitrary strings prefixed by `l:`. The values after `l:` get passed to `cpr_lInvoke` when they should get invoked. Labelled properties can be used to introduce properties in the UI that aren't "real" Java methods. This can be useful in ["wrapper"](https://github.com/lu-cs-sde/codeprober/tree/master/minimal-probe-wrapper) implementations. | ast   |
| cpr_lInvoke(String)            | any                          | null          | Invoked when a labelled property from `cpr_propertyListShow` is being evaluated. The argument is the string after `l:`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      | ast   |
| cpr_extraAstReferences         | `String[]` or `List<String>` | null          | A list of property names (like `cpr_propertyListShow`) that resolve to AST node references that should be included as extra connections in the AST view. For example this can be used to add higher-order attributes, or point out remote references betwen nodes in the AST.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                | ast   |
| cpr_lGetChildName(String)      | String                       | null          | In JastAdd, child accessor methods have an annotation that describes the name of the child in the AST specification. This name is extracted and rendered in the AST view. In addition, all child accessors are placed on top of the list of properties when creating a new probe. `cpr_lGetChildName` can be used to make a labelled property be treated as a child accessor. It is invoked with the value after `l:`, like `cpr_lInvoke`.                                                                                                                                                                                                                                                                                                                                   | ast   |
| cpr_lGetAspectName(String)     | String                       | null          | In JastAdd, attributes have an annotation that describe which `aspect` declaration they belong to. This is used in the CodeProber UI to group the list of properties when creating a new probe. `cpr_lGetAspectName` can be used to make a labelled property get an aspect grouping too. It is invoked with the value after `l:`, like `cpr_lInvoke`.                                                                                                                                                                                                                                                                                                                                                                                                                        | ast   |
| cpr_setTraceReceiver(Consumer) | void                         |               | Install a trace receiver, which should be invoked whenever an attribute begins of finishes evaluation. This is part of the "Capture traces" checkbox in the CodeProber UI. See `Capture Traces` section below for more detail.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | root  |
| cpr_getOutput                  | any                          |               | Overrides how something is presented in the result of a probe evaluation. CodeProber supports extracting data from- and presenting- many standard datastructures by default, including arrays and anything that implements `java.util.Collection` or `java.lang.Iterable`. However, custom datastructures can use `cpr_getOutput` to define how to extract data from a given type. This logic is applied recursively: values returned by `cpr_getOutput` can also implement `cpr_getOutput` themselves, or be arrays/`java.util.Collection`'s, etc. A way to implement `cpr_getOutput` is to return an object array, like `public Object[] cpr_getOutput() { return new Object[] { foo, bar }; }`.                                                                           | any   |
| cpr_getDiagnostic              | String                       |               | Similar to `cpr_getOutput`, this lets you associate a diagnostic string with the result of a probe evaluation. These format for diagnostic strings are documented in the CodeProber ui: click the triple-dot menu in a probe, followed by `Magic output messages help`. For example, to render a warning (yellow) squiggly between line 5 column 7 is and line 5 column 10, `cpr_getDiagnostic` should return `WARN@20487;20490;Hover message here`. The numbers are packed line/column bits, like "Variant 3" in the Mandatory API above. See "Diagnostic class" below for example of how `cpr_getOutput` and `cpr_getDiagnostic` can be used.                                                                                                                              | any   |
| cpr_ppPrefix                   | String                       | null          | Add a prefix to the "Pretty Print" view. This appears before the first child value.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | ast   |
| cpr_ppInfix(int)               | String                       | null          | Add an infix between children in the "Pretty Print view". The argument is in the range `[0, num_children - 1]`. Between child 0 and 1 the argument is `0`, between child 1 and 2 the argument is 1, etc.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | ast   |
| cpr_ppPrefix                   | String                       | null          | Add a suffix to the "Pretty Print" view. This appears after the last child value.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            | ast   |
| cpr_describeParentConnection   | `Object[]`                   | null          | Describe how a given node is connected to its parent node. This is used during node locator construction. It is intended to be used to connect results of higher-order attributes to their origin. In JastAdd-based tools, CodeProber can infer this automatically At the time of writing the returned array must contain exactly one string, which is the name of a higher-order attribute. If required, this can be expanded to support for example parameterized attributes too.                                                                                                                                                                                                                                                                                          | ast   |
# Diagnostic class
A common use case for CodeProber is to inspect the result of semantic analysis. The results of such an analysis can both be presented as squiggly lines in the editor, and as text in a probe result area.
This is achieved by having a class that implements both the `cpr_getOutput` and `cpr_getDiagnostic` APIs, like this:
```java
public class Diagnostic {
    Object humanReadable;
    String diagnostic;

    public Diagnostic(Object humanReadable, String diagnostic) {
      this.humanReadable = humanReadable;
      this.diagnostic = diagnostic;
    }

    public Object cpr_getOutput() { return humanReadable; }
    public String cpr_getDiagnostic() { return diagnostic; }
}
```
The two fields in `Diagnostic` represent the probe result area and text editor, respectively.
For example (using JastAdd syntax), the following attribute will create a error squiggly line between line 3, column 5 and line 3, column 7:
```
syn Object Node.demo() = new Diagnostic(
	"This is shown in the probe window"
	String.format("ERR@%d;%d;%s", (3 << 12) + 5, (3 << 12) + 7, "This is shown when the squiggly is hovered")
);
```

# Capture Traces
The `cpr_setTraceReceiver` API requires a little more explanation.

In the CodeProber settings panel there is a checkbox with the label `Capture traces`.
This allows you to capture information about indirect dependencies of properties.
For this to work, the AST root must have a function `cpr_setTraceReceiver` that looks like this:

```java
public void cpr_setTraceReceiver(java.util.function.Consumer<Object[]> recv) {
  // 'recv' records trace information.
  // You should assign it to a field for later use.
  this.theTraceReceiver = recv;
}

// Later, when something should be added to a trace, call it
// It will be non-null if `Capture traces` is checked.
if (this.theTraceReceiver != null) {
  this.theTraceReceiver.accept(new Object[]{ ... })
}
```

If `recv` is called while CodeProber is evaluating a property, then the information there will be visible in the CodeProber UI, *if* the user has checked `Capture traces`.

## Kinds of trace events
CodeProber will `toString` the first element in the object array to identify the kinds of trace event.
Currently, two types of events are supported, and they match tracing events produced by JastAdd:

### COMPUTE_BEGIN
Expected structure:
```
["COMPUTE_BEGIN", ASTNode node, String attribute, Object params, Object value]
```
Example invocation to `recv` in `cpr_setTraceReceiver`:
```java
recv.accept(new Object[]{ "COMPUTE_BEGIN", someAstNode, "foo()", null, null })
```
### COMPUTE_END
Expected structure:
```
["COMPUTE_END", ASTNode node, String attribute, Object params, Object value]
```
Example invocation to `recv` in `cpr_setTraceReceiver`:
```java
recv.accept(new Object[]{ "COMPUTE_END", someAstNode, "foo()", null, "ResultValue" })
```
## Tracing locator issues
Tracing can be tricky to get right. You may get errors in the terminal where you started `codeprober.jar` stating something like:
```
Failed creating locator for AstNode< [..]
```
This happens if one of the AST nodes passed to a trace events aren't attached to the AST anymore. This can happen for example if you mutate the tree through rewrites.
You can try toggling the `flush tree first` checkbox under `Capture traces` on and off. You can also try changing the `cache strategy` values back and forth. Some combination of the two might work.

If changing the settings doesn't work, then you must change which events are reported to CodeProber. Try to avoid setting the `ASTNode` arguments to nodes that get removed from the tree.

## Supporting Tracing In JastAdd
In JastAdd, compile with the flag `--tracing=all`, and then implement `cpr_setTraceReceiver` as follows:
```
public void Program.cpr_setTraceReceiver(final java.util.function.Consumer<Object[]> recv) {
  trace().setReceiver(new ASTState.Trace.Receiver() {
    @Override
    public void accept(ASTState.Trace.Event event, ASTNode node, String attribute, Object params, Object value) {
      recv.accept(new Object[] { event, node, attribute, params, value });
    }
  });
}
```

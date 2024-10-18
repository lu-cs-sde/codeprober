# MinimalProbeWrapper - a minimal "wrapper" implementation

There are two main strategies for integrating your tool with CodeProber.
1) Make sure the AST of your tool follows one of the structures CodeProber expects (for more details, see AstNodeApiStyle.java).
2) Create a wrapper that acts as a bridge between CodeProber and your tool.

Option 1 is in theory the best option, as it doesn't require you to create specific code for CodeProber.
Everything built with JastAdd should be able to use option 1 without any extra modifications needed.
However, in some cases it isn't possible or practical for you to change an existing AST, in which case option 2 is better.

Option 2 is much more flexible, but requires a little more manual work. This directory contains a minimal probe wrapper implementation that can help you get started.

## Slightly less minimal implementation

Once you have gotten the minimal wrapper to work with your tool, you may want to create a slightly less "minimal" version.
The minimal implementation ([RootNode.java](src/mpw/RootNode.java)) here only has a single root node in the AST. For a richer experience in CodeProber, you should ideally have one wrapper node for each node in the actual AST.
In addition, those nodes should have real text spans rather than the single hardcoded span as in the minimal root node.
Provided below is a class that looks similar to [RootNode.java](src/mpw/RootNode.java), but with some additional methods and comments to help implement the AST hierarchy correctly.

```java
class MyWrapper {
  // `Node` is assumed to be a real non-wrapper AST node from your tool.
  Node n;
  MyWrapper parent;
  MyWrapper[] children;

  private MyWrapper[] getChildren() {
    // Lazily initialized for performance reasons.
    // Iterate over all child nodes in n, wrap in 'MyWrapper'.
    // Set parent on the wrapped nodes to `this`.
    // assign list to this.children.
    // return this.children.
    // It is important that the same 'Node' is only wrapped *once*
    // This is because CodeProber uses identity comparison, so wrapping the same 'Node' twice
    // would create two different wrapper instances that won't pass identity comparison.
  }

  public int getNumChild() { return getChildren().length; }
  public MyWrapper getChild(int idx) { return getChildren()[idx]; }
  public MyWrapper getParent() { return parent; }

  // Replace n.X() with correct values for your node
  public int getStartLine() { return n.X(); }
  public int getStartColumn() { return n.X(); }
  public int getEndLine() { return n.X(); }
  public int getEndColumn() { return n.X(); }

  public String cpr_nodeLabel() {
    // Optional method, can return a label here that will presented in the UI instead of 'MyWrapper'.
    // This allows you to differentiate between different node types, even though you are using a wrapper.
  }

  // It is still important to implement cpr_propertyListShow,
  // just as in the minimal RootNode.java
  public java.util.List<String> cpr_propertyListShow() {
    // ...
  }
}
```

The live node tracking used in CodeProber doesn't work quite the same for wrapper implementations, since all node types are the same ("MyWrapper.class"), and node types are an important part of making the tracking resilient.
One way to get very similar tracking performance is to make sure that cpr_nodeLabel is implemented on all your nodes, and then set the system property `cpr.type_identification_style` to `NODE_LABEL`. E.g:
```
  java -Dcpr.type_identification_style=NODE_LABEL -jar CodeProber.jar my-minimal-wrapper.jar
```

`type_identification_style=NODE_LABEL` isn't as exhausively tested as the default reflection/class-based identification style. You may run into some issues. If you do and it is reproducible, please open an issue report! Our goal is to make the two styles equally viable.

## Building

Build using the build.sh file:

```bash
./build.sh
```
The result can be found in my-minimal-wrapper.jar.

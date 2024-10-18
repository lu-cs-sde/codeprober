package mpw;

public class RootNode {

  private final String src;

  public RootNode(String src) {
    this.src = src;
  }

  // There are a few magic functions you can implement to improve the CodeProber experience.
  // All of them are prefixed with "cpr_" (from 'CodePRober').
  // One of the most important is cpr_propertyListShow, which controls which properties
  // should appear in the list of properties inside CodeProber.
  // Can return String[] or java.util.List<String>
  public String[] cpr_propertyListShow() {
    // Property names can either be names of normal Java methods in this class,
    // or "labeled properties", which are encoded with "l:TheLabelHere".
    // When labeled properties should be evaluated, cpr_lInvoke
    // is called with the label as an argument.
    return new String[] {
      "echoSourceText",
      "l:labeled_1",
      "l:labeled_2"
    };
  }

  // Example of a normal Java method that is accessible as a property in CodeProber.
  public Object echoSourceText() {
    return "The source text that CodeProber gave us is: " + src;
  }

  public Object cpr_lInvoke(String propName) {
    // 'propName' is the contents after "l:", i.e "labled_1" or "labeled_2" in our case.
    switch (propName) {
      case "labeled_1": return "Labeled property 1 invoked";
      case "labeled_2": return "Labeled property 2 invoked";
      default: throw new IllegalArgumentException(String.format("Tried invoking labeled property '%s'", propName));
    }
    // Instead of a string switch/case as above, cpr_lInvoke is potentially a good place
    // to tunnel requests to your language tool if it runs outside the JVM.
    // Send the string out, collect the response and return it synchronously from here.
    // If this is a slow process but your tool is able to handle requests concurrently,
    // consider setting "--concurrent=N" when launching CodeProber. E.g:
    //   java -jar CodeProber.jar --concurrent=4 my-minimal-wrapper.jar
    // The command above will configure CodeProber to evaluate properties in up to 4 separate background processes,
    // which can speed up user interaction in the tool, especially with multiple active probes.
  }

  // Some required functions for AST traversal below.
  // In our minimal example the AST only has a single node, so the values are hardcoded.
  public int getStart() { return (1 << 12) + 1; }
  public int getEnd() { return (999 << 12) + 1; }
  public int getNumChild() { return 0; }
  public Object getChild(int i) { return null; }
  public Object getParent() { return null; }
}

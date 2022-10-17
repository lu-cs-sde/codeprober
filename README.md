# CodeProber - Source code based exploration of program analysis results

An implementation of property probes.

Quick overview of features (5 minutes): https://www.youtube.com/watch?v=d-KvFy5h9W0

Installation & getting started: https://www.youtube.com/watch?v=1beyfNhUQEg

## Compatibility

To use CodeProber, you must have a compiler or analyzer that follows certain conventions.
Any tool built using [JastAdd](https://jastadd.cs.lth.se/web/) follow these conventions automatically. Support for non-JastAdd tools (and formalizing the conventions) is planned future work (also, see 'I didn't use JastAdd' below).

Even if you built your tool with JastAdd, there are ~2 lines of code you need to add.
In your main java file, add the following declaration:

```java
public static Object DrAST_root_node;
```

Then, immediately after parsing the input file(s), assign the root of your AST to this variable. E.g:

```java
Program myAst = parse(...);
DrAST_root_node = myAst;
```

The `DrAST_root_node` variable will be used by CodeProber as an entry point for most of its functionality.
For information about the name "DrAST", see https://bitbucket.org/jastadd/drast/src/master/.

## Running

In general, the tool tries to print helpful messages when needed, so you shouldn't have to read this file to get started.
Try starting the tool without any arguments and it will tell you the following:
```
Usage: java -jar code-prober.jar path/to/your/analyzer-or-compiler.jar [args-to-forward-to-compiler-on-each-request]
```

Once started, you should open http://localhost:8000 in your browser.
When the page is loaded, you'll find a `Help` button on the right side which can help you further.

### Environment variables

There are a few optional environment variables that can be set.
| Key      | Default value | Description |
| ----------- | ----------- | ----------- |
| WEB_SERVER_PORT | 8000 | The port to serve HTML/JS/etc from. This is the port you visit in your browser, e.g the '8000' in 'http://localhost:8000'. |
| WEBSOCKET_SERVER_PORT   | 8080        | The port to run the websocket server on. This isn't visible to the user, only change it if necessary (such as if you have another process listening on port 8080). |
| PERMIT_REMOTE_CONNECTIONS   | false        | Whether or not to permit remote (non-local) connections to the server. Most compilers/analyzers read/write files on your computer based on user input. By allowing remote connections, you open up a potential vulnerability. Only set to true when on a trusted network, or if running inside a sandboxed environment. |
| WEB_RESOURCES_OVERRIDE   | null        | A file path that should be used to serve web resources (e.g HTML/JS/etc). If null, then resources are read from the classpath. Setting this can be benificial during development of the code prober tool itself (set it to `client/public/`), but there is very little point in setting it for normal tool users. |

Example invocation where some of these are set:
```sh
WEB_SERVER_PORT=8005 WEB_RESOURCES_OVERRIDE=client/public/ java -jar code-prober.jar /path/to/your/compiler/or/analyzer.jar
```

## Troubleshooting

CodeProber should run on any OS on Java 8 and above. However, sometimes things don't work as they should. This section has some known issues and their workarounds.

### CodeProber is running, but I cannot access localhost:8000 in my browser

By default, CodeProber only accepts requests from localhost. When you run CodeProber inside a container (for example WSL or Docker) then requests from your host machine can appear as remote, not local. To solve this, add the `PERMIT_REMOTE_CONNECTIONS` environment variable mentioned above.

### System.exit/SecurityManager problem

If you run Java version 18+ then you'll likely run into error messages that mention "Failed installing System.exit interceptor".
For many language tools, the main function behaves like this:

1) Parse the incoming document
2) Perform semantic analysis, print results
3) If any errors were detected, call System.exit(1);

To avoid the System.exit call killing the CodeProber process, CodeProber uses `System.setSecurityManager(..)` to intercept all calls to System.exit.
As of Java 18, this feature is disabled by default. You can re-enable it by adding the system property 'java.security.manager=allow'. I.e run CodeProber with:

```bash
java -Djava.security.manager=allow -jar code-prober.jar path/to/your/analyzer-or-compiler.jar [args-to-forward-to-compiler-on-each-request]
```

For more information about this issue, see https://openjdk.org/jeps/411 and https://bugs.openjdk.org/browse/JDK-8199704.

### My problem isn't listed above

Check the terminal where you started code-prober.jar If no message there helps you, please open an issue in this repository!

## Building - Client

The client is built with TypeScript. Do the following to generate the JavaScript files:

```sh
cd client/ts
npm install
npm run bw
```

Where 'bw' stands for 'build --watch'.
All HTML, JavaScript and CSS files should now be available in the `client/public` directory.

## Build - Server

The server is written in Java and has primarily been developed in Eclipse, and there are `.project` & `.classpath` files in the repository that should let you import the project into your own Eclipse instance.
That said, release builds should be performed on the command line. To build, do the following:

```sh
cd server
./build.sh
```

If your git status is non-clean (any untracked/staged/modified files present), then this will generate a single file: `code-prober-dev.jar`.

If your git status is clean, then this will instead generate two files: `code-prober.jar` and `VERSION`.

`code-prober.jar` is the tool itself.

`VERSION` is a file containing the current git hash. This is used by the client to detect when new versions are available.

If you want to "deploy" a new version, i.e make the tiny "New version available" prompt appear in the corner for everybody using the tool, then you must first commit your changes, make sure `git status` says `working tree clean`, and then build, and commit again.

## I didn't use JastAdd, what now?

As mentioned above, the API that CodeProber demands of the ASTs found in `DrAST_root_node` hasn't been formalized yet.
However, if you are willing to explore a bit yourself then looking in [AstNodeApiStyle.java](server/src/codeprober/metaprogramming/AstNodeApiStyle.java) is a good start. It is an enum describing different styles of "AST APIs". CodeProber tries to detect which style is present by experimentally invoking some methods within each style.
You can also look in [TestData.java->Node](server/src-test/codeprober/ast/TestData.java), which shows the non-JastAdd node implementation used for test cases.

CodeProber needs to know a shared supertype of all AST nodes. Any subtype of this supertype is expected to implement the "API style" mentioned above. CodeProber tries to automatically detect the common supertype with the following pseudocode:

```
def find_super(type)
  if (type.supertype.package == type.package)
    return find_super(type.supertype)
  else
    return type
```

`find_super` is called with `DrAST_root_node`. In other words, it finds the top supertype that belongs to the same package as `DrAST_root_node`.

If you already have an AST structure and don't want/cannot modify it, you can create a wrapper class that fulfills the API above. Something like:

```java

class MyWrapper {
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

  // This method can be used to make properties appear in the property list
  // even when the user hasn't checked 'Show all properties'.
  public java.util.List<String> cpr_propertyListShow() {
    return java.util.Arrays.asList("foo", "bar");
  }
  public X foo() { ... }
  public Y bar() { ... }
}
```

At the time of writing, the live node tracking used in CodeProber doesn't work well with wrapper implementations, since all types will be the same. This might be improved in the future, at least for nodes that implement cpr_nodeLabel().

## Artifact

If you want to try CodeProber, but don't have an analysis tool of your own, you can follow the instructions in the artifact to our Property probe paper, found here: https://doi.org/10.5281/zenodo.7185242.
This will let you use CodeProber with a Java compiler/analyzer called IntraJ (https://github.com/lu-cs-sde/IntraJ).

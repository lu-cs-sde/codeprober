# Download and Run

1) Download [codeprober.jar](https://github.com/lu-cs-sde/codeprober/releases/latest) from the latest release.
2) Start like this:
    ```
    java -jar codeprober.jar [--test] [--concurrent=N] your-analyzer-or-compiler.jar [args-to-forward-to-compiler-on-each-request]
    ```

For example, if you have codeprober.jar in your downloads directory, and your tool is called `compiler.jar` and is located in your home directory, then run:
```
java -jar ~/Downloads/codeprober.jar ~/compiler.jar
```

Once started, you should open [http://localhost:8000](http://localhost:8000) in your browser.
When the page is loaded, you'll find a `Help` button on the right side which can help you further.
See [Features](features.md) for features you can use once inside CodeProber.

## AddNum Compiler

If you would like to try CodeProber but you don't have an analyzer or compiler of your own, you can use the latest version of [AddNum](https://github.com/lu-cs-sde/codeprober/blob/master/addnum/AddNum.jar). This is a very small example "compiler" for a language that only supports additions and numbers. Download it, and then run CodeProber with e.g:

```bash
java -jar ~/Downloads/codeprober.jar ~/Downloads/AddNum.jar
```

## Compatibility with Custom Tool

To use CodeProber, you must have a compiler or analyzer (a "tool") that follows certain conventions.
There are two main things the tool must provide:

1) A way for CodeProber to turn a given text file into an AST ("parsing").
2) A way for CodeProber to traverse nodes in the AST.

Any tool built using [JastAdd](https://jastadd.cs.lth.se/web/) supports AST traversal automatically.
For non-JastAdd tools, please see the "Mandatory API" section in [AST API](../config/ast_api.md).

There are two options for parsing.

The first option is preferred, and it is to add a method `CodeProber_parse` method in your main class.
It can look like this:

```java
public static Object CodeProber_parse(String[] args) throws Throwable {
  // 'args' has at least one entry.
  // First are all optional args (see "args-to-forward-to-compiler-on-each-request" above).
  // The last entry in the array is path to a source file containing the CodeProber editor text.
  String sourceFile = args[args.length - 1];
  // "parse" is expected to take the path to a source file and transform it to the root of an AST
  return parse(sourceFile);
}
```
CodeProber will invoke this and use the return value as the entry point into your AST.

The second option is for CodeProber to use your normal main method as an entry point.
Since main cannot return anything, the resulting AST must instead be assigned to a static field within your main class.
In total, there are therefore two changes that are required:
In your main java file, add the following declaration:

```java
public static Object CodeProber_root_node;
```

Then, immediately after parsing the input file(s), assign the root of your AST to this variable. E.g:

```java
Program myAst = parse(...);
CodeProber_root_node = myAst;
```

The `CodeProber_root_node` variable will be used by CodeProber as an entry point.
Since many tools perform semantic analysis and call System.exit in main if an error is encountered, CodeProber attempts to install a System.exit interceptor when using the main method.
This has issues on Java versions 17 and later (see "System.exit/SecurityManager problem" in [Troubleshooting](troubleshooting.md)).
If you have problems with this, consider using `CodeProber_parse` instead, which doesn't rely on intercepting System.exit.

If you define both `CodeProber_parse` and `CodeProber_root_node`, then `CodeProber_parse` takes precedence.

If you previously used [DrAST](https://bitbucket.org/jastadd/drast/src/master/), then you likely have `DrAST_root_node` declared and assigned.
CodeProber will use this as a fallback if neither `CodeProber_parse` nor `CodeProber_root_node` is not defined, so you don't have to do any changes.
However, the help/warning messages inside `CodeProber` that reference the root node will reference `CodeProber_root_node`, even if you don't have it.
So for a more consistent experience, consider adding a specific declaration for CodeProber (or even better, use `CodeProber_parse`).

## Wrapper Implementations

Perhaps you read the [Mandatory AST API](../config/ast_api.md) and thought that it doesn't fit with your tool at all. Perhaps your AST isn't implemented in a JastAdd-like object-oriented fashion, or perhaps it even runs outside the JVM. There is a solution for you which works almost as well, and that is to create a "wrapper implementation".

In short, a wrapper implementation is one that creates a very shallow wrapper AST around another "real" AST.
It is up to the wrapper to delegate all requests regarding AST traversal, method invocation, etc to the real AST. This can be done via reflection, sockets, native code, etc. CodeProber doesn't know and doesn't care where the "real" implementation resides.

The fastest way to get started with a wrapper is to start from the [minimal-prober-wrapper example implementation](https://github.com/lu-cs-sde/codeprober/tree/master/minimal-probe-wrapper) found in the CodeProber repository.
Once you have gotten a minimum working example, you can look at the [AST APIs](../config/ast_api.md) to improve the experience.
Several APIs are designed specifically for wrapper implementations, such as `cpr_lInvoke` and `cpr_nodeLabel`.

One thing to note about wrappers: CodeProber needs to know a shared supertype of all AST nodes. Any subtype of this supertype is expected to implement the mandatory AST traversal APIs mentioned above. CodeProber tries to automatically detect the common supertype with the following pseudocode:

```
def find_super(type)
  if (type.supertype.package == type.package)
    return find_super(type.supertype)
  else
    return type
```

`find_super` is called with your AST root (returned from `CodeProber_parse` or `CodeProber_root_node`). In other words, it finds the top supertype that belongs to the same package as the AST root.
If your native AST structure uses a hierarchy of packages rather than a single flat package, then this will likely cause problems, so you should probably rely on a wrapper implementation instead.

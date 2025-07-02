# CodeProber - Source code based exploration of program analysis results

An implementation of property probes.

Quick overview of features (5 minutes): https://www.youtube.com/watch?v=d-KvFy5h9W0

Installation & getting started: https://www.youtube.com/watch?v=1beyfNhUQEg

## Getting started

1) Download [codeprober.jar](https://github.com/lu-cs-sde/codeprober/releases/latest) from the latest release.
2) Start like this:
    ```
    java -jar codeprober.jar your-analyzer-or-compiler.jar [args-to-forward-to-compiler-on-each-request]
    ```

For example, if you have codeprober.jar in your downloads directory, and your tool is called `compiler.jar` and is located in your home directory, then run:
```
java -jar ~/Downloads/codeprober.jar ~/compiler.jar
```

Once started, you should open http://localhost:8000 in your browser.
When the page is loaded, you'll find a `Help` button on the right side which can help you further.

## Compatibility

To use CodeProber, you must have a compiler or analyzer that follows certain conventions.
Any tool built using [JastAdd](https://jastadd.cs.lth.se/web/) follow these conventions automatically. Support for non-JastAdd tools (and formalizing the conventions) is planned future work (also, see 'I didn't use JastAdd' below).

Even if you built your tool with JastAdd, you must add some way for CodeProber to transform a source file into an AST.
There are two options.

The first option is preferered, and it is to add a method `CodeProber_parse` method in your main class.
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
This has issues on newer versions of java (see "System.exit/SecurityManager problem" below).
If you have problems with this, consider using `CodeProber_parse` instead, which doesn't rely on intercepting System.exit.

If you define both `CodeProber_parse` and `CodeProber_root_node`, then `CodeProber_parse` takes precedence.

If you previously used `DrAST` (https://bitbucket.org/jastadd/drast/src/master/), then you likely have `DrAST_root_node` declared and assigned.
CodeProber will use this as a fallback if neither `CodeProber_parse` nor `CodeProber_root_node` is not defined, so you don't have to do any changes.
However, the help/warning messages inside `CodeProber` that reference the root node will reference `CodeProber_root_node`, even if you don't have it.
So for a more consistent experience, consider adding a specific declaration for CodeProber (or even better, use `CodeProber_parse`).

### Configuration

There are a few optional environment variables and system properties that can be set.
These are documented in the [docs](docs/README.md).

Example invocation where some of these are set:
```sh
PORT=8005 WEB_RESOURCES_OVERRIDE=client/public/ java -jar codeprober.jar /path/to/your/compiler/or/analyzer.jar
```

## Troubleshooting

CodeProber should run on any OS on Java 8 and above. However, sometimes things don't work as they should. This section has some known issues and their workarounds.

### CodeProber is running, but I cannot access localhost:8000 in my browser

By default, CodeProber only accepts requests from localhost. When you run CodeProber inside a container (for example WSL or Docker) then requests from your host machine can appear as remote, not local. To solve this you have two options:

1) Use the URL printed to the terminal when you start CodeProber. It contains an authorization key that enables non-local access.
   If connecting to a non-localhost url, please make sure the "?auth=some_key_here" part of the URL printed to the terminal is included.
2) Add the `PERMIT_REMOTE_CONNECTIONS` environment variable mentioned above.

### System.exit/SecurityManager problem

If you run Java version 17+ then you may run into error messages that mention "Failed installing System.exit interceptor".
For many language tools, the main function behaves like this:

1) Parse the incoming document
2) Perform semantic analysis, print results
3) If any errors were detected, call System.exit(1);

To avoid the System.exit call killing the CodeProber process, CodeProber uses `System.setSecurityManager(..)` to intercept all calls to System.exit.
As of Java 17, this feature is disabled by default. You can re-enable it by adding the system property 'java.security.manager=allow'. I.e run CodeProber with:

```bash
java -Djava.security.manager=allow -jar codeprober.jar path/to/your/analyzer-or-compiler.jar [args-to-forward-to-compiler-on-each-request]
```

Alterntiavely, add a `CodeProber_parse` method as mentioned above in the `Compatibility` section.
Here, CodeProber does not use a System.exit interceptor, so this issue will not appear.

For more information about this issue, see https://openjdk.org/jeps/411 and https://bugs.openjdk.org/browse/JDK-8199704.

### My problem isn't listed above

Check the terminal where you started codeprober.jar If no message there helps you, please open an issue in this repository!

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

This will generate `codeprober.jar`.

Release builds are performed via releases in Github.
Create a new release and publish it.
Within a few minutes, a Github action runner (`.github/workflows/release-build.yml`) will push a freshly built and tested `codeprober.jar` to your release.
If no jar file is pushed, then the release build failed.
Please look at the action runner log to debug why.
Release builds can be retried by editing the release in any way.

## I didn't use JastAdd, what now?

It is possible that your AST will just "magically" work with CodeProber. Add `CodeProber_parse` or `CodeProber_root_node` as described above and just try running with it.
CodeProber has a few different styles of ASTs it tries to detect and interact with, see [AST API](docs/ast_api.md).

If that doesn't work, the quickest way to get started is to use the [minimal-prober-wrapper example implementation](minimal-probe-wrapper).

If you have a little more time and want to create a richer CodeProber experience then the best thing would be to adapt to one of the AST structures CodeProber expects. [AstNodeApiStyle.java](server/src/codeprober/metaprogramming/AstNodeApiStyle.java) is an enum representing the different options currently supported. CodeProber tries to detect which style is present by experimentally invoking some methods within each style.
For some more examples, you can also look in [TestData.java->Node](server/src-test/codeprober/ast/TestData.java), which shows the non-JastAdd node implementation used for test cases.

CodeProber needs to know a shared supertype of all AST nodes. Any subtype of this supertype is expected to implement the "AST API style" mentioned above. CodeProber tries to automatically detect the common supertype with the following pseudocode:

```
def find_super(type)
  if (type.supertype.package == type.package)
    return find_super(type.supertype)
  else
    return type
```

`find_super` is called with your AST root (returned from `CodeProber_parse` or `CodeProber_root_node`). In other words, it finds the top supertype that belongs to the same package as the AST root.
If your native AST structure uses a hierarchy of packages rather than a single flat package, then this will likely cause problems so you should probably rely on a wrapper type instead.

## Artifact

If you want to try CodeProber, but don't have an analysis tool of your own, you can try out the playground at https://github.com/Kevlanche/codeprober-playground/.

You can also download the artifact to our Property probe paper, found here: https://doi.org/10.5281/zenodo.7185242.

Both options let you use CodeProber with a Java compiler/analyzer called IntraJ (https://github.com/lu-cs-sde/IntraJ).

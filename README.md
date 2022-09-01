# CodeProber - Source code based exploration of program analysis results

An implementation of property probes.

⚠️ In active development ⚠️

## Compatibility

To use CodeProber, you must have a compiler or analyzer that follows certain conventions.
Any tool built using [JastAdd](https://jastadd.cs.lth.se/web/) follow these conventions automatically. Support for non-JastAdd tools (and formalizing the conventions) is planned future work.

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

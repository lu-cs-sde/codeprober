# Building

CodeProber consists of three main parts: client, server and a protocol between the client and server.

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
Within a few minutes, a Github action runner ([.github/workflows/release-build.yml](https://github.com/lu-cs-sde/codeprober/blob/master/.github/workflows/release-build.yml)) will push a freshly built and tested `codeprober.jar` to your release.
If no jar file is pushed, then the release build failed.
Please look at the action runner log to debug why.
Release builds can be retried by editing the release in any way.

## Build - Protocol

The client and server communicate over JSON-RPC-like messages. They are either delivered over websocket, or through HTTP PUT requests.
The structure of the data is described in the `protocol` directory.
This structure can be automatically converted to Java and TypeScript. This way, there is only one source of truth for the communication between client and server, and we have some form of type safety.

If you make changes to the protocol you need to refresh the generated Java/TypeScript files.
To build it, do the following.

```sh
cd protocol
./build.sh
```

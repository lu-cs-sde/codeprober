# Troubleshooting

CodeProber should run on any OS on Java 8 and above. However, sometimes things don't work as they should. This section has some known issues and their workarounds.

## I cannot access localhost:8000 in my browser

By default, CodeProber only accepts requests from localhost. When you run CodeProber inside a container (for example WSL or Docker) then requests from your host machine can appear as remote, not local. To solve this you have two options:

1) Use the URL printed to the terminal when you start CodeProber. It contains an authorization key that enables non-local access.
   If connecting to a non-localhost url, please make sure the "?auth=some_key_here" part of the URL printed to the terminal is included.
2) Add the `PERMIT_REMOTE_CONNECTIONS` environment variable mentioned in [Environment Variables](../config/environment_variables.md).

## System.exit/SecurityManager problem

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

Alterntiavely, add a `CodeProber_parse` method as mentioned in [Download and Run](download_and_run.md).
Here, CodeProber does not use a System.exit interceptor, so this issue will not appear.

For more information about this issue, see [https://openjdk.org/jeps/411](https://openjdk.org/jeps/411) and [https://bugs.openjdk.org/browse/JDK-8199704](https://bugs.openjdk.org/browse/JDK-8199704).

## My problem isn't listed above

Check the terminal where you started codeprober.jar If no message there helps you, please [open an issue](https://github.com/lu-cs-sde/codeprober/issues)!

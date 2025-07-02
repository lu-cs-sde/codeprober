Documentation of APIs and configurations that are available when working with CodeProber.

- [ast_api](ast_api.md) - programmatic API you can implement in your code. Contains a small number of required methods for AST traversal.
- [environment_variables](environment_variables.md) & [system_properties](system_properties.md) - Environment variables and system properties that control how CodeProber behaves.

During development, it was unfortunately not well defined what functionality should be handled as an environment variable vs a system property.
- Environment variables are generally settings that are common in many tools, for example controlling the port of the webserver, and performance-related limits.
- System properties are generally more specific to CodeProber - enabling/controlling specific features in CodeProber.
If you would like to be able to control something as an environment variable but it currently is a system property, or vice versa, pleae open an issue or pull request.

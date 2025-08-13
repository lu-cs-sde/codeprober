# Features

CodeProber usage is described and demonstrated in the following resources:

- [YouTube: 5 minute overview](https://www.youtube.com/watch?v=d-KvFy5h9W0)
- [Paper: Publication in JSS](https://doi.org/10.1016/j.jss.2024.111980)
- [YouTube: Demonstration at LIVE 2023 (13 minutes)](https://www.youtube.com/watch?v=lkTJ4VL0xtY)

## Basic Features

Listed below are a number of basic features supported in CodeProber.

### Creating Probe from Code
![](../media/create_probe.png)
Right click anywhere in the CodeProber editor to create a new probe.

### Creating Probe from Reference
![Feature image](../media/create_probe_from_reference.png)
If the output of a probe is an AST reference, you can click it to create a new probe. This allows you to iteratively follow references across the AST.

### AST View
![Feature image](../media/ast_view.png)
When selecting the property name while creating a new probe, you can click the triple dots, and then "Render AST downwards". This will show the AST structure of tree of the node in question.

### Pretty Print
![Feature image](../media/pretty_print_view.png)
In the same dropdown where you create the AST view, you can also select "Pretty Print" to access a pretty-printed representation of your AST. For this to work well you must implement the `cpr_pp*` methods described in [AST API](../config/ast_api.md).

### Workspace
![Feature image](../media/workspace.png)
Using the [system property](../config/system_properties.md) `-Dcpr.workspace`, you can expose a filesystem in the CodeProber UI. Any changes you make in the UI will be saved to disk, and any changes to disk will be loaded into in the UI.

### Text Probe
![Feature image](../media/text_probe.png)
CodeProber interprets text in the format `[[A.b.c]]` as a "text probe". Text probes are evaluated by CodeProber, and the results are rendered in a blue box the editor. If you additionally add `=d`, it will treat the probe as an assertion, and show a green or red box depending on if the assertion passed or not. For example, in the image it evaluated `AddExpr.constant` and compared it with `3`. The comparison succeeded, so the box is green.

Three comparison operators are available: `=`, `!=` and `~=`. They perform equals, not-equals and contains (as in String.contains) comparison respectively. Contains can for example be usedful when checking error messages, as they are often quite long and you mostly want to verify that they are there. You can for example write: `CallExpr.errors~=Expected int`, to check that `errors` contains "Expected int" somewhere in the error message(s).

### Testing
![Feature image](../media/cli_testing.png)
By combining a workspace and text probes with assertion, you can write persistent unit tests for your AST.
The tests can be evaluated inside the CodeProber UI by pressing the "Run Tests" button (only visible when a workspace is active).
The tests can also be run in a command-line using something similar to the following command:
```bash
java -Dcpr.workspace=path/to/workspace -jar codeprober.jar --test yourtool.jar
```
The exit code (`$?`) is set to 0 or 1 as expected on success/failure.
This allows you to run your assertions for example in a CI/CD pipeline.

### Diagnostics
![Feature image](../media/squiggly_line.png)
The CodeProber text editor can display diagnostics in the form of squiggly lines and arrows.
There are two ways to make diagnostics appear:

1) Print a "diagnostic string" to stdout while a probe is being evaluated
2) Return an object that implements the [cpr_getDiagnostic API](../config/ast_api.md), which returns a "diagnostic string".

A diagnostic string is any string that follows one of the following patterns:

- ERR@S;E;MSG
- WARN@S;E;MSG
- INFO@S;E;MSG
- HINT@S;E;MSG
- LINE-PP@S;E;COL
- LINE-PA@S;E;COL
- LINE-AP@S;E;COL
- LINE-AA@S;E;COL

**ERR**, **WARN**, **INFO** and **HINT** are used for different severity squiggly lines.
**S** and **E** are line and column bits packed into a single 32 bit integer in the format 0xLLLLLCCC. For example line 5 column 7 is `20487` (`(5 << 12) + 7`).
**MSG** is any string.
**COL** is a string in the form `#RGBA`.
All arrow types start with **LINE-**. The suffix after that describes the style of arrow:
- PP (plain-plain) - just a line, no arrow
- PA (plain-arrow) - line in the starting point, arrow in the ending point
- AP (arrow-plain) - opposite of PA.
- AA (arrow-arrow) - an arrow on both sides.
If you want to point *from* the start *to* the end, then you want to use **LINE-PA**.

See also the Diagnostic class in the [AST API](../config/ast_api.md).

## Advanced Features

There are a number of features in CodeProber which are undocumented. They may simply be new, or considered too advanced/unstable for general use.
If you are relying on something not listed in the table above, please reach out (for example by opening an [issue](https://github.com/lu-cs-sde/codeprober/issues)), and we can work on documenting/stabilizing it.

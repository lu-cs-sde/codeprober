package mpw;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Collectors;

public class MinimalProbeWrapper {

  public static Object CodeProber_root_node;

  public static void main(String[] args) throws IOException {

    // The last arg is always a file with the contents inside the CodeProber window.
    // The non-last arg(s) are extra arguments, either set in the command-line when starting CodeProber,
    // or via "Override main args" in the CodeProber client.
    final File srcFile = new File(args[args.length - 1]);
    final String src = Files.readAllLines(srcFile.toPath()).stream().collect(Collectors.joining("\n"));

    // Here we would typically parse 'src' into an AST, and set the result of that parse to CodeProber_root_node.
    // However in this minimal implementation there is no parser, so we just keep 'src' as-is.
    CodeProber_root_node = new RootNode(src);
  }
}

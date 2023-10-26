package mpw;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Collectors;

public class MinimalProbeWrapper {

  public static Object CodeProber_parse(String[] args) throws IOException {
    // The last arg is always a file with the contents inside the CodeProber window.
    // The non-last arg(s) are extra arguments, either set in the command-line when starting CodeProber,
    // or via "Override main args" in the CodeProber client.
    final File srcFile = new File(args[args.length - 1]);

    // Here we would typically parse 'src' into an AST and return it (optionally inside a wrapper).
    // However in this minimal implementation there is no parser, so we just keep 'src' as-is.
    final String src = Files.readAllLines(srcFile.toPath()).stream().collect(Collectors.joining("\n"));
    return new RootNode(src);
  }

  public static void main(String[] args) throws IOException {
    System.out.println("MinimalProbeWrapper started normally");
    System.out.println("Here you'd perform your normal application logic.");
    System.out.println("CodeProber uses 'CodeProber_parse' as an entry point.");
  }
}

package codeprober.textprobe;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class TextProbe {

	public static Object CodeProber_parse(String[] args) throws IOException {
		if (args.length != 1) {
			throw new IllegalArgumentException("Expected exactly one argument");
		}

		final byte[] bytes = Files.readAllBytes((new File(args[args.length - 1]).toPath()));
		final String src = new String(bytes, 0, bytes.length, StandardCharsets.UTF_8);
		return Parser.parse(src, '{', '}');
	}

	public static void main(String[] args) {
		System.out.println("This is the main method - it is not meant to be directly invoked");
		System.out.println("Explore text probes in CodeProber instead. Run `serve.sh`");
	}
}

package addnum;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Collectors;

import addnum.ast.Add;
import addnum.ast.Node;
import addnum.ast.Num;
import addnum.ast.Program;

public class AddNum {

	public static Object CodeProber_root_node;

	private int line = 1, column = 1;
	private int parseIndex = 0;
	private final String src;

	public AddNum(String src) {
		this.src = src;
	}

	public static void main(String[] args) throws IOException {
		System.out.println("Started AddNum");
		try {
			final String src = Files.readAllLines(new File(args[0]).toPath()).stream()
					.collect(Collectors.joining("\n"));
			System.out.println("parsing " + src);
			CodeProber_root_node = new Program(new AddNum(src).parseTop());
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	private int makePos() {
		return (line << 12) + column;
	}

	private Node err(String msg) {
		final int pos = makePos();
		System.out.printf("ERR@%d;%d;%s\n", pos, pos + 1, msg);
		throw new RuntimeException("Failed parsing");
	}

	private void skipWhitespace() {
		while (parseIndex < src.length()) {
			final char ch = src.charAt(parseIndex);
			System.out.println("Matching " + (int) ch);
			switch (ch) {
			case ' ': // Normal space
			case ' ': // nbsp
			case '\t':
			case '\r': {
				++column;
				break;
			}
			case '\n': {
				column = 1;
				++line;
				break;
			}

			default: {
				return;
			}
			}
			++parseIndex;
		}
	}

	private void ensureNext(char c) {
		final char ch = src.charAt(parseIndex);
		if (ch == c) {
			++parseIndex;
			++column;
			return;
		}
		err("Expected '" + c + "', got '" + ch + "'");
	}

	private Node parseTop() {
		Node ret = parse();
		while (parseIndex < src.length()) {
			skipWhitespace();
			if (parseIndex < src.length() && src.charAt(parseIndex) == '+') {
				ret = parseAdd(ret);
			} else {
				break;
			}
		}
		return ret;
	}

	private Node parse() {
		skipWhitespace();

		if (parseIndex >= src.length()) {
			--parseIndex;
			return err("Expected node, got EOF");
		}

		final char ch = src.charAt(parseIndex);
		if (ch >= '0' && ch <= '9') {
			return parseNum();
		}

		if (ch == '(') {
			ensureNext('(');
			final Node ret = parseTop();
			skipWhitespace();
			ensureNext(')');
			++column;
			return ret;
		}

		return err("Unexpected char '" + ch + "' (numeric: " + (int) ch + ")");
	}

	private Node parseAdd(Node lhs) {
		ensureNext('+');
		return new Add(lhs, parse());
	}

	private Node parseNum() {
		int val = 0;

		final int start = makePos();
		while (parseIndex < src.length()) {
			final char ch = src.charAt(parseIndex);
			if (ch >= '0' && ch <= '9') {
				val = val * 10 + (ch - '0');
				++column;
				++parseIndex;
			} else {
				break;
			}
		}
		return new Num(start, makePos() - 1, val);
	}
}

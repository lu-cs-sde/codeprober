package codeprober.protocol.create;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.junit.Test;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.ast.TestData;
import codeprober.protocol.data.RpcBodyLine;

public class TestEncodeResponseValue {

	private static class CustomType {
		private String strValue;

		public CustomType(String strValue) {
			this.strValue = strValue;
		}

		@Override
		public String toString() {
			return strValue;
		}
	}

	private List<RpcBodyLine> encodeValue(Object value) {
		final AstInfo info = TestData.getInfo(new AstNode(TestData.getSimple()));
		final List<RpcBodyLine> lines = new ArrayList<>();
		EncodeResponseValue.encodeTyped(info, lines, null, value, new HashSet<>());
		return lines;
	}

	@Test
	public void testEncodeCustomMultiline() {
		final List<RpcBodyLine> lines = encodeValue(new CustomType("Foo\nBar"));
		assertEquals(2, lines.size());
		for (RpcBodyLine l : lines) {
			assertEquals(RpcBodyLine.Type.plain, l.type);
		}
		assertEquals("Foo", lines.get(0).asPlain());
		assertEquals("Bar", lines.get(1).asPlain());
	}

	@Test
	public void testEncodeCustomMultilineWithCarriageReturn() {
		final List<RpcBodyLine> lines = encodeValue(new CustomType("Foo\r\nBar"));
		assertEquals(2, lines.size());
		for (RpcBodyLine l : lines) {
			assertEquals(RpcBodyLine.Type.plain, l.type);
		}
		assertEquals("Foo", lines.get(0).asPlain());
		assertEquals("Bar", lines.get(1).asPlain());
	}
}

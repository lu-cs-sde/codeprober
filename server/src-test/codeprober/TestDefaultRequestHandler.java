package codeprober;

import org.json.JSONArray;
import org.json.JSONObject;

import codeprober.ast.AstNode;
import codeprober.ast.TestData;
import codeprober.locator.CreateLocator;
import codeprober.metaprogramming.Reflect;
import junit.framework.TestCase;

public class TestDefaultRequestHandler extends TestCase {

	public void testHandleSemiComplicatedRequest() {
		DefaultRequestHandler handler = new DefaultRequestHandler("n/a", new String[0]);

		final AstNode ast = new AstNode(TestData.getWithSimpleNta());
		final AstInfo info = TestData.getInfo(ast);

		final JSONObject requestObj = new JSONObject();
		requestObj.put("posRecovery", info.recoveryStrategy.toString());

		final JSONObject query = new JSONObject();
		requestObj.put("query", query);

		query.put("locator", CreateLocator.fromNode(info,
				new AstNode(Reflect.invoke0(ast.underlyingAstNode, "simpleNTA")).getNthChild(info, 1)));

		final JSONObject attr = new JSONObject();
		query.put("attr", attr);

		attr.put("name", "timesTwo");
		final JSONArray args = new JSONArray();
		attr.put("args", args);

		final JSONObject intArg = new JSONObject();
		intArg.put("type", "int");
		intArg.put("value", 21);
		intArg.put("isNodeType", false);
		args.put(intArg);

		final JSONObject retBuilder = new JSONObject();
		final JSONArray bodyBuilder = new JSONArray();

		handler.handleParsedAst(ast.underlyingAstNode, info.loadAstClass, requestObj, retBuilder, bodyBuilder);

		assertEquals(1, bodyBuilder.length());
		assertEquals("42", bodyBuilder.get(0));
	}

}

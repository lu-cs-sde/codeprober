package pasta;

import org.json.JSONArray;
import org.json.JSONObject;

import junit.framework.TestCase;
import pasta.ast.AstNode;
import pasta.ast.TestData;
import pasta.locator.CreateLocator;
import pasta.metaprogramming.Reflect;

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
				new AstNode(Reflect.invoke0(ast.underlyingAstNode, "simpleNTA")).getNthChild(1)));

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

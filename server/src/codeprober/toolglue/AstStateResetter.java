package codeprober.toolglue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Proxy implementation of {@link UnderlyingTool} that additionally calls
 * '.state().reset()' on the result of {@link #parse(String[])}, in order to
 * reset any potential global circular state in JastAdd.
 */
public class AstStateResetter implements UnderlyingTool {

	private final UnderlyingTool proxyTarget;

	public AstStateResetter(UnderlyingTool proxyTarget) {
		this.proxyTarget = proxyTarget;
	}

	@Override
	public long getVersionId() {
		return proxyTarget.getVersionId();
	}

	@Override
	public ParseResult parse(String[] args) {
		final ParseResult result = proxyTarget.parse(args);
		if (result.rootNode != null) {
			// Even though this is a fresh parse, we must reset global states that may or
			// may not eixst. In JastAdd, this is done via '.state().reset()'
			try {
				final Method stateAccessor = result.rootNode.getClass().getMethod("state");
				final String retName = stateAccessor.getReturnType().getName();
				// Some guard rails to avoid accidentally calling state().reset() on non-JastAdd
				// tools.
				if (retName.endsWith(".ASTState") || retName.endsWith("$ASTState")) {
					final Object stateObj = stateAccessor.invoke(result.rootNode);
					if (stateObj != null) {
						final Method resetMth = stateObj.getClass().getMethod("reset");
						if (resetMth.getReturnType() == Void.TYPE) {
							resetMth.invoke(stateObj);
						}
					}
				}
			} catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | RuntimeException e) {
				// Don't print anything, this is optional functionality, not available in all
				// tools.
			}
		}
		return result;
	}
}

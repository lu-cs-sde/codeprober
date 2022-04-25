package pasta;

import java.util.function.Function;

import pasta.ast.AstNode;
import pasta.protocol.PositionRecoveryStrategy;

public class AstInfo {

	public final AstNode ast;
	public final PositionRecoveryStrategy recoveryStrategy;
	public final Function<String, Class<?>> loadAstClass;
	public final Class<?> basAstClazz;
	
	public AstInfo(AstNode ast, PositionRecoveryStrategy recoveryStrategy,
			Function<String, Class<?>> loadAstClass) {
		this.ast = ast;
		this.recoveryStrategy = recoveryStrategy;
		this.loadAstClass = loadAstClass;

		Class<?> baseAstType = ast.underlyingAstNode.getClass();
		while (true) {
			Class<?> parentType = baseAstType.getSuperclass();
			if (parentType == null
					|| !parentType.getPackage().getName().equals(baseAstType.getPackage().getName())) {
				break;
			}
			baseAstType = parentType;
		}
		this.basAstClazz = baseAstType;
	}
}

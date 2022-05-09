package pasta;

import java.util.function.Function;

import pasta.ast.AstNode;
import pasta.metaprogramming.PositionRepresentation;
import pasta.protocol.PositionRecoveryStrategy;

public class AstInfo {

	public final AstNode ast;
	public final PositionRecoveryStrategy recoveryStrategy;
	public final PositionRepresentation positionRepresentation;
	public final Function<String, Class<?>> loadAstClass;
	public final Class<?> basAstClazz;

	public AstInfo(AstNode ast, PositionRecoveryStrategy recoveryStrategy,
			PositionRepresentation positionRepresentation, Function<String, Class<?>> loadAstClass) {
		this.ast = ast;
		this.recoveryStrategy = recoveryStrategy;
		this.positionRepresentation = positionRepresentation;
		this.loadAstClass = loadAstClass;

		Class<?> baseAstType = ast.underlyingAstNode.getClass();
		while (true) {
			Class<?> parentType = baseAstType.getSuperclass();
			if (parentType == null || !parentType.getPackage().getName().equals(baseAstType.getPackage().getName())) {
				break;
			}
			baseAstType = parentType;
		}
		this.basAstClazz = baseAstType;
	}

	public String getQualifiedAstType(String simpleName) {
		if (basAstClazz.getEnclosingClass() != null) {
			return basAstClazz.getEnclosingClass().getName() + "$" + simpleName;
		}
		return basAstClazz.getPackage().getName() + "." + simpleName;
	}
}

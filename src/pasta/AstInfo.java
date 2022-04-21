package pasta;

import java.util.function.Function;

import pasta.protocol.PositionRecoveryStrategy;

public class AstInfo {

	public final Object ast;
	public final PositionRecoveryStrategy recoveryStrategy;
	public final Function<String, Class<?>> loadAstClass;
	public final Class<?> basAstClazz;
	
	public AstInfo(Object ast, PositionRecoveryStrategy recoveryStrategy,
			Function<String, Class<?>> loadAstClass) {
		this.ast = ast;
		this.recoveryStrategy = recoveryStrategy;
		this.loadAstClass = loadAstClass;

		Class<?> baseAstType = ast.getClass();
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

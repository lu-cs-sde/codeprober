package codeprober;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import codeprober.ast.AstNode;
import codeprober.metaprogramming.AstNodeApiStyle;
import codeprober.metaprogramming.InvokeProblem;
import codeprober.metaprogramming.Reflect;
import codeprober.metaprogramming.TypeIdentificationStyle;
import codeprober.protocol.PositionRecoveryStrategy;

public class AstInfo {

	public final AstNode ast;
	public final PositionRecoveryStrategy recoveryStrategy;
	public final AstNodeApiStyle astApiStyle;
	public final Function<String, Class<?>> loadAstClass;
	public final Class<?> baseAstClazz;
	public final TypeIdentificationStyle typeIdentificationStyle;

	private Class<?> locatorTALRoot;
	private boolean loadedLocatorTALRoot;

	private final Map<Class<?>, Map<String, Boolean>> hasOverride0Cache = new HashMap<>();
	private final Map<Class<?>, Map<String, Map<Class<?>, Boolean>>> hasOverride1Cache = new HashMap<>();

	public AstInfo(AstNode ast, PositionRecoveryStrategy recoveryStrategy, AstNodeApiStyle astApiStyle,
			TypeIdentificationStyle typeIdentificationStyle) {
		this.ast = ast;
		this.recoveryStrategy = recoveryStrategy;
		this.astApiStyle = astApiStyle;
		this.loadAstClass = cname -> {
			try {
				return Class.forName(cname, true, ast.underlyingAstNode.getClass().getClassLoader());
			} catch (ClassNotFoundException e) {
				System.err.println("Type '" + cname + "' cannot be instantiated by the AST's ClassLoader");
				e.printStackTrace();
				throw new RuntimeException(e);
			}
		};
		this.typeIdentificationStyle = typeIdentificationStyle;

		Class<?> baseAstType = ast.underlyingAstNode.getClass();
		while (true) {
			Class<?> parentType = baseAstType.getSuperclass();
			if (parentType == null || !parentType.getPackage().getName().equals(baseAstType.getPackage().getName())) {
				break;
			}
			baseAstType = parentType;
		}
		this.baseAstClazz = baseAstType;
	}

	public String getQualifiedAstType(String simpleName) {
		if (baseAstClazz.getEnclosingClass() != null) {
			return baseAstClazz.getEnclosingClass().getName() + "$" + simpleName;
		}
		return baseAstClazz.getPackage().getName() + "." + simpleName;
	}

	public Class<?> getLocatorTALRoot() {
		if (!loadedLocatorTALRoot) {
			loadedLocatorTALRoot = true;
			try {
				String underlyingType = (String) Reflect.invoke0(ast.underlyingAstNode, "cpr_locatorTALRoot");
				locatorTALRoot = loadAstClass.apply(getQualifiedAstType(underlyingType));
			} catch (InvokeProblem e) {
				// OK, this is an optional attribute after all
			} catch (ClassCastException e) {
				System.out.println("cpr_locatorTALRoot returned non-String value");
				e.printStackTrace();
			}
		}
		return locatorTALRoot;
	}

	public boolean hasOverride0(Class<?> cls, String mthName) {
		Map<String, Boolean> inner = hasOverride0Cache.get(cls);
		if (inner == null) {
			inner = new HashMap<>();
			hasOverride0Cache.put(cls, inner);
		}

		Boolean ex = inner.get(mthName);
		if (ex != null) {
			return ex;
		}

		boolean fresh;
		try {
			cls.getMethod(mthName);
			fresh = true;
		} catch (NoSuchMethodException e) {
			fresh = false;
		}
		inner.put(mthName, fresh);
		return fresh;
	}

	public boolean hasOverride1(Class<?> cls, String mthName, Class<?> argType) {
		Map<String, Map<Class<?>, Boolean>> inner = hasOverride1Cache.get(cls);
		if (inner == null) {
			inner = new HashMap<>();
			hasOverride1Cache.put(cls, inner);
		}

		Map<Class<?>, Boolean> innerer = inner.get(mthName);
		if (innerer == null) {
			innerer = new HashMap<>();
			inner.put(mthName, innerer);
		}

		Boolean ex = innerer.get(argType);
		if (ex != null) {
			return ex;
		}

		boolean fresh;
		try {
			cls.getMethod(mthName, argType);
			fresh = true;
		} catch (NoSuchMethodException e) {
			fresh = false;
		}
		innerer.put(argType, fresh);
		return fresh;
	}

}

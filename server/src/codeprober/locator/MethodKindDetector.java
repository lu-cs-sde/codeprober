package codeprober.locator;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import codeprober.metaprogramming.Reflect;

public class MethodKindDetector {

	public static boolean isNta(Method m) {
		final Annotation a = extractAttributeAnnotation(m);
		return a != null && (Boolean) Reflect.invoke0(a, "isNTA");
	}

	public static boolean isSomeAttr(Method m) {
		return extractAttributeAnnotation(m) != null;
	}

	private static Annotation extractAttributeAnnotation(Method m) {
		if (!Modifier.isPublic(m.getModifiers())) {
			return null;
		}

		for (Annotation a : m.getAnnotations()) {
			if (a.annotationType().getName().endsWith(".ASTNodeAnnotation$Attribute")) {
				return a;
			}
		}
		return null;

	}

	public static boolean looksLikeAUserAccessibleJastaddRelatedMethod(Method m) {
		if (!Modifier.isPublic(m.getModifiers())) {
			return false;
		}

		for (Annotation a : m.getAnnotations()) {
			if (a.annotationType().getName().contains(".ASTNodeAnnotation$")) {
				return true;
			}
		}
		return false;
	}

	public static String getAstChildName(Method m) {
		for (Annotation a : m.getAnnotations()) {
			final String name = a.annotationType().getName();
			if (name.contains(".ASTNodeAnnotation$Child") //
					|| name.contains(".ASTNodeAnnotation$OptChild") //
					|| name.contains(".ASTNodeAnnotation$ListChild") //
					|| name.contains(".ASTNodeAnnotation$Token")) {
				return (String) Reflect.invoke0(a, "name");
			}
		}
		return null;
	}

	public static String getRelatedAspect(Method m) {
		for (Annotation a : m.getAnnotations()) {
			final String name = a.annotationType().getName();
			if (name.contains(".ASTNodeAnnotation$Source")) {
				return (String) Reflect.invoke0(a, "aspect");
			}
		}
		return null;
	}
}
//

package codeprober.textprobe.ast;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public interface ASTNodeAnnotation {
	@Retention(RetentionPolicy.RUNTIME)
	public @interface Attribute {
		boolean isNTA() default false;
	}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface Child {
		String name();
	}
}

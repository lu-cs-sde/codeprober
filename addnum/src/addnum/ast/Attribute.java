package addnum.ast;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

interface ASTNodeAnnotation {
	@Retention(RetentionPolicy.RUNTIME)
	@interface Attribute {
		boolean isNTA() default true;
	}

	@Retention(RetentionPolicy.RUNTIME)
	@interface Child {
		String name();
	}

}
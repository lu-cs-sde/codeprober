package codeprober.protocol;

import java.util.Locale;

public enum ParameterTypeDetail {
	NORMAL,
	AST_NODE,
	OUTPUTSTREAM;
	
	public static ParameterTypeDetail decode(String s) {
		try {
			return ParameterTypeDetail.valueOf(s.toUpperCase(Locale.ENGLISH));
		} catch (IllegalArgumentException e) {
			System.out.println("Invalid ParameterTypeDetail value '" + s +"'");
			e.printStackTrace();
			return null;
		}
	}
}

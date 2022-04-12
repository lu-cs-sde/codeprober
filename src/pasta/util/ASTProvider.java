package pasta.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.jar.JarFile;

/**
 * Provides an AST by running a compiler and using reflection to fetch the
 * JastAdd AST from the compiler.
 * <p>
 * Created by gda10jth on 1/15/16.
 */
public class ASTProvider {
	/**
	 * Runs the target compiler.
	 */
	public static boolean parseAst(String jarPath, String[] args,
			BiConsumer<Object, Function<String, Class<?>>> rootConsumer) {
		try {
			File jarFile = new File(jarPath);
			CompilerClassLoader urlClassLoader = new CompilerClassLoader(jarFile.toURI().toURL());

			// Find and instantiate the main class from the Jar file.
			try (JarFile jar = new JarFile(jarFile)) {
				String mainClassName = jar.getManifest().getMainAttributes().getValue("Main-Class");
				Class<?> klass = Class.forName(mainClassName, true, urlClassLoader);

				// Find the main method we are looking for and invoke the method to get the new
				// root.
				try {
					long start = System.currentTimeMillis();
					try {
						SystemExitControl.disableSystemExit();
						Method mainMethod = klass.getMethod("main", String[].class);
						mainMethod.invoke(null, new Object[] { args });
					} catch (InvocationTargetException e) {
						if (!(e.getTargetException() instanceof SystemExitControl.ExitTrappedException)) {
							e.printStackTrace();
							System.err.println(
									"compiler error : " + (e.getMessage() != null ? e.getMessage() : e.getCause()));
							return false;
						}
					} finally {
						SystemExitControl.enableSystemExit();
						System.out.printf("Compiler finished after : %d ms\n", (System.currentTimeMillis() - start));
					}
					Field rootField = klass.getField("DrAST_root_node");
					rootField.setAccessible(true);
					Object root = rootField.get(klass);
					rootConsumer.accept(root, otherCls -> {
						try {
							return Class.forName(otherCls, true, urlClassLoader);
						} catch (ClassNotFoundException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
							throw new RuntimeException(e);
						}
					});
					return true;
				} catch (NoSuchMethodException e) {
					System.err.println("Could not find the compiler's main method.");
				} catch (IllegalAccessException e) {
					e.printStackTrace();
				} finally {
					SystemExitControl.enableSystemExit();
				}
			}
		} catch (FileNotFoundException e) {
			System.err.println("Could not find jar file, check path");
			e.printStackTrace();
		} catch (Throwable e) {
			e.printStackTrace();
		}
		SystemExitControl.enableSystemExit();
		return false;
	}
}

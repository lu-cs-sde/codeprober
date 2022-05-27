package pasta.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
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
	private static class LoadedJar {
		public final String jarPath;
		public final long jarLastModified;
		public final CompilerClassLoader classLoader;
		public final Class<?> mainClazz;
		public final JarFile jar;
		public final Method mainMth;
		public final Field drAstField;

		public LoadedJar(String jarPath, long jarLastModified, CompilerClassLoader classLoader, Class<?> mainClazz, JarFile jar,
				Method mainMth, Field drAstField) {
			this.jarPath = jarPath;
			this.jarLastModified = jarLastModified;
			this.classLoader = classLoader;
			this.mainClazz = mainClazz;
			this.jar = jar;
			this.mainMth = mainMth;
			this.drAstField = drAstField;
		}
	}

	public static void purgeCache() {
		if (lastJar != null) {
			try {
				lastJar.jar.close();
			} catch (IOException e) {
				System.out.println("Error when closing jar file");
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		lastJar = null;
	}
	
	private static LoadedJar lastJar = null;

	private static LoadedJar loadJar(String jarPath)
			throws ClassNotFoundException, IOException, NoSuchMethodException, SecurityException, NoSuchFieldException {
		if (hasUnchangedJar(jarPath)) {
			return lastJar;
		}
		if (lastJar != null) {
			lastJar.jar.close();
		}
		final File jarFile = new File(jarPath);
		final long jarLastMod = jarFile.lastModified();
		CompilerClassLoader urlClassLoader = new CompilerClassLoader(jarFile.toURI().toURL());

		// Find and instantiate the main class from the Jar file.
		JarFile jar = new JarFile(jarFile);
		String mainClassName = jar.getManifest().getMainAttributes().getValue("Main-Class");
		Class<?> klass = Class.forName(mainClassName, true, urlClassLoader);
		Method mainMethod = klass.getMethod("main", String[].class);
		Field rootField = klass.getField("DrAST_root_node");
		rootField.setAccessible(true);

		lastJar = new LoadedJar(jarPath, jarLastMod, urlClassLoader, klass, jar, mainMethod, rootField);
		return lastJar;
	}
	
	public static boolean hasUnchangedJar(String jarPath) {
		File jarFile = new File(jarPath);
		final long jarLastMod = jarFile.lastModified();
		return lastJar != null && lastJar.jarPath.equals(jarPath) && lastJar.jarLastModified == jarLastMod;
	}

	/**
	 * Runs the target compiler.
	 */
	public static boolean parseAst(String jarPath, String[] args,
			BiConsumer<Object, Function<String, Class<?>>> rootConsumer) {
		try {
			LoadedJar ljar = loadJar(jarPath);

			// Find the main method we are looking for and invoke the method to get the new
			// root.
			try {
				long start = System.currentTimeMillis();
				try {
					SystemExitControl.disableSystemExit();
					ljar.mainMth.invoke(null, new Object[] { args });
				} catch (InvocationTargetException e) {
					System.out.println("ASTPRovider caught " + e);
					e.printStackTrace();
					System.out.println("target: " + e.getTargetException());
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
				Object root = ljar.drAstField.get(ljar.mainClazz);
				rootConsumer.accept(root, otherCls -> {
					try {
						return Class.forName(otherCls, true, ljar.classLoader);
					} catch (ClassNotFoundException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						throw new RuntimeException(e);
					}
				});
				return true;
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} finally {
				SystemExitControl.enableSystemExit();
			}
		} catch (NoSuchMethodException e) {
			System.err.println("Could not find the compiler's main method.");
		} catch (NoSuchFieldException e) {
			System.err.println("Could not find the compiler's main method.");
		} catch (FileNotFoundException e) {
			System.err.println("Could not find jar file, check path");
			e.printStackTrace();
		} catch (Throwable e) {
			e.printStackTrace();
		}
		SystemExitControl.enableSystemExit();
		return false;
	}

//	public static boolean parseAst(String jarPath, String[] args,
//			BiConsumer<Object, Function<String, Class<?>>> rootConsumer) {
//		try {
//			File jarFile = new File(jarPath);
//			CompilerClassLoader urlClassLoader = new CompilerClassLoader(jarFile.toURI().toURL());
//
//			// Find and instantiate the main class from the Jar file.
//			try (JarFile jar = new JarFile(jarFile)) {
//				String mainClassName = jar.getManifest().getMainAttributes().getValue("Main-Class");
//				Class<?> klass = Class.forName(mainClassName, true, urlClassLoader);
//
//				// Find the main method we are looking for and invoke the method to get the new
//				// root.
//				try {
//					long start = System.currentTimeMillis();
//					try {
//						SystemExitControl.disableSystemExit();
//						Method mainMethod = klass.getMethod("main", String[].class);
//						mainMethod.invoke(null, new Object[] { args });
//					} catch (InvocationTargetException e) {
//						System.out.println("ASTPRovider caught " + e);
//						e.printStackTrace();
//						System.out.println("target: " + e.getTargetException());
//						if (!(e.getTargetException() instanceof SystemExitControl.ExitTrappedException)) {
//							e.printStackTrace();
//							System.err.println(
//									"compiler error : " + (e.getMessage() != null ? e.getMessage() : e.getCause()));
//							return false;
//						}
//					} finally {
//						SystemExitControl.enableSystemExit();
//						System.out.printf("Compiler finished after : %d ms\n", (System.currentTimeMillis() - start));
//					}
//					Field rootField = klass.getField("DrAST_root_node");
//					rootField.setAccessible(true);
//					Object root = rootField.get(klass);
//					rootConsumer.accept(root, otherCls -> {
//						try {
//							return Class.forName(otherCls, true, urlClassLoader);
//						} catch (ClassNotFoundException e) {
//							// TODO Auto-generated catch block
//							e.printStackTrace();
//							throw new RuntimeException(e);
//						}
//					});
//					return true;
//				} catch (NoSuchMethodException e) {
//					System.err.println("Could not find the compiler's main method.");
//				} catch (IllegalAccessException e) {
//					e.printStackTrace();
//				} finally {
//					SystemExitControl.enableSystemExit();
//				}
//			}
//		} catch (FileNotFoundException e) {
//			System.err.println("Could not find jar file, check path");
//			e.printStackTrace();
//		} catch (Throwable e) {
//			e.printStackTrace();
//		}
//		SystemExitControl.enableSystemExit();
//		return false;
//	}
}

package codeprober.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarFile;

import codeprober.metaprogramming.StdIoInterceptor;
import codeprober.protocol.data.RpcBodyLine;
import codeprober.toolglue.ParseResult;

/**
 * Provides an AST by running a compiler and using reflection to fetch the
 * JastAdd AST from the compiler.
 * <p>
 * Originally by gda10jth on 1/15/16, modified for CodeProber use.
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

		public LoadedJar(String jarPath, long jarLastModified, CompilerClassLoader classLoader, Class<?> mainClazz,
				JarFile jar, Method mainMth, Field drAstField) {
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
		Field rootField = null;

		// Support two declarations: primarily 'CodeProber_root_node' but fall back to
		// 'DrAST_root_node'.
		try {
			rootField = klass.getField("CodeProber_root_node");
		} catch (NoSuchFieldException e) {
			rootField = klass.getField("DrAST_root_node");
		}
		if (rootField == null) {
			jar.close();
			throw new NoSuchFieldException("Neither CodeProber_root_node nor DrAST_root_node defined");
		}
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
	public static ParseResult parseAst(String jarPath, String[] args) {
		System.out.println("parsing w/ args: " + Arrays.toString(args));
		boolean installedSystemExitInterceptor = false;
		try {
			LoadedJar ljar = loadJar(jarPath);

			// Find the main method we are looking for and invoke the method to get the new
			// root.
			try {
				long start = System.currentTimeMillis();
				Object prevRoot = ljar.drAstField.get(ljar.mainClazz);
				List<RpcBodyLine> captures = null;
				try {
					System.setProperty("java.security.manager", "allow");
					try {
						SystemExitControl.disableSystemExit();
						installedSystemExitInterceptor = true;
					} catch (UnsupportedOperationException uoe) {
						uoe.printStackTrace();
						captures = StdIoInterceptor.performDefaultCapture(() -> {
							System.err.println("Failed installing System.exit interceptor");
							System.err.println(
									"Restart code-prober.jar with the system property 'java.security.manager=allow'");
							System.err.println("Example:");
							System.err.println(
									"   java -Djava.security.manager=allow -jar path/to/code-prober.jar path/to/your/analyzer-or-compiler.jar");
							System.err.println("Alternatively, avoid calling System.exit() if running in CodeProber.");
							System.err.println(
									"This can be determined by checking if the system property CODEPROBER is true");
							System.err.println("For example, use the following 'customExit' method instead of System.exit:");
							System.err.println("  static void customExit() {");
							System.err.println(
									"    if (System.getProperty(\"CODEPROBER\", \"false\").equals(\"true\")) { throw new Error(\"Simulated exit\"); }");
							System.err.println("    else { System.exit(1); }");
							System.err.println("  }");
							System.setProperty("CODEPROBER", "true");
						});
//						return new ParseResult(null, captures);
					}

					final AtomicReference<Exception> innerError = new AtomicReference<>();
					final List<RpcBodyLine> mainCaptures = StdIoInterceptor.performDefaultCapture(() -> {
						try {
							ljar.mainMth.invoke(null, new Object[] { args });
						} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
							innerError.set(e);
						}
					});
					if (captures != null) {
						mainCaptures.addAll(captures);
					} else {
						captures = mainCaptures;
					}
					if (innerError.get() != null) {
						throw innerError.get();
					}
				} catch (InvocationTargetException e) {
					System.out.println("ASTPRovider caught " + e.getTargetException());
					final Throwable target = e.getTargetException();
					final boolean expectedException = target instanceof SystemExitControl.ExitTrappedException || target instanceof Error;
					if (!expectedException) {
						e.printStackTrace();
						System.err.println(
								"compiler error : " + (e.getMessage() != null ? e.getMessage() : e.getCause()));
						return new ParseResult(null, captures);
					}
				} finally {
					if (installedSystemExitInterceptor) {
						SystemExitControl.enableSystemExit();
					}

					System.out.printf("Compiler finished after : %d ms%n", (System.currentTimeMillis() - start));
				}
				Object root = ljar.drAstField.get(ljar.mainClazz);
				if (root == null) {
					if (captures == null) {
						captures = new ArrayList<>();
					}
					captures.addAll(StdIoInterceptor.performDefaultCapture(() -> {
						System.err.println("Compiler exited, but no 'CodeProber_root_node' found.");
						System.err.println(
								"If parsing failed, you can draw 'red squigglies' in the code to indicate where it failed.");
						System.err.println("See overflow menu (⠇) -> \"Magic output messages help\".");
						System.err.println(
								"If parsing succeeded, make sure you declare and assign the following field in your main class:");
						System.err.println("'public static Object CodeProber_root_node'");
					}));
				} else if (root == prevRoot) {
					// Parse ended without unexpected error (System.exit is expected), but nothing
					// changed
					if (captures == null) {
						captures = new ArrayList<>();
					}
					captures.addAll(StdIoInterceptor.performDefaultCapture(() -> {
						System.err.println(
								"CodeProber_root_node didn't change after main invocation, treating this as a parse failure.");
						System.err.println(
								"If you perform semantic checks and call System.exit(..) if you get errors, then please do so *after* assigning CodeProber_root_node");
						System.err.println(
								"I.e do 1: parse. 2: update CodeProber_root_node. 3: perform semantic checks (optional)");
					}));
					return new ParseResult(null, captures);
				}

				return new ParseResult(root, captures);
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} finally {
				if (installedSystemExitInterceptor) {
					SystemExitControl.enableSystemExit();
				}
			}
		} catch (NoSuchMethodException e) {
			System.err.println("Could not find the compiler's main method.");
		} catch (NoSuchFieldException e) {
			System.err.println("Could not find the AST root declaration.");
			final List<RpcBodyLine> userHelp = StdIoInterceptor.performDefaultCapture(() -> {
				System.err.println(
						"'CodeProber_root_node' not found. Make sure you declare and assign the following field in your main class:");
				System.err.println("'public static Object CodeProber_root_node'");
			});
			return new ParseResult(null, userHelp);
		} catch (FileNotFoundException e) {
			System.err.println("Could not find jar file, check path");
			e.printStackTrace();
		} catch (Throwable e) {
			e.printStackTrace();
		}
		if (installedSystemExitInterceptor) {
			SystemExitControl.enableSystemExit();
		}
		return new ParseResult(null, null);
	}
}

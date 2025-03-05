package codeprober.util;

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;

public class CompilerClassLoader extends ClassLoader {
  private final ChildClassLoader childClassLoader;

  public CompilerClassLoader(URL... classpath) {
    super(Thread.currentThread().getContextClassLoader());
    childClassLoader = new ChildClassLoader(classpath, new DetectClass(this.getParent()));
  }

  @Override protected synchronized Class<?> loadClass(String name, boolean resolve)
      throws ClassNotFoundException {
    try {
      return childClassLoader.findClass(name);
    } catch (ClassNotFoundException e) {
      return super.loadClass(name, resolve);
    }
  }

  @Override public InputStream getResourceAsStream(String name) {
    final InputStream childResult = childClassLoader.getResourceAsStream(name);
    if (childResult != null){
      return childResult;
    }
    return super.getResourceAsStream(name);
  }

  private static class ChildClassLoader extends URLClassLoader {
    private final DetectClass realParent;

    public ChildClassLoader(URL[] urls, DetectClass realParent) {
      super(urls, null);
      this.realParent = realParent;
    }

    @Override public Class<?> findClass(String name) throws ClassNotFoundException {
      try {
        Class<?> loaded = super.findLoadedClass(name);
        if (loaded != null) {
          return loaded;
        }
        return super.findClass(name);
      } catch (ClassNotFoundException e) {
        return realParent.loadClass(name);
      }
    }

    // TODO handle https://bugs.openjdk.org/browse/JDK-8246714
    @Override public InputStream getResourceAsStream(String name) {
      return super.getResourceAsStream(name);
    }
  }

  private static class DetectClass extends ClassLoader {
    public DetectClass(ClassLoader parent) {
      super(parent);
    }

    @Override public Class<?> findClass(String name) throws ClassNotFoundException {
      return super.findClass(name);
    }
  }
}

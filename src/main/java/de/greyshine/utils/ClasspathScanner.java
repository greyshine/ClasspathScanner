package de.greyshine.utils;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import sun.misc.Launcher;

/**
 * Classpath scanning and filtering.
 * 
 * @author dirk schumacher
 */
@SuppressWarnings({ "restriction", "unused" })
public class ClasspathScanner {

	/**
	 * Handler for handling a resource entry on the classpath
	 */
	public static interface IHandler {

		/**
		 * @param inResource
		 */
		void handle(IResource inResource);
	}

	/**
	 * A representation for a resource on the classpath
	 */
	public static interface IResource {

		/**
		 * @return {@link ClassLoader} which declares this {@link IResource}
		 */
		ClassLoader getClassloader();

		/**
		 * @return
		 */
		Path getPath();

		/**
		 * @return
		 */
		String getResourceName();

		/**
		 * @return <code>true</code> if candidate to be loaded by its
		 *         {@link ClassLoader} otherwise <code>false</code>
		 */
		boolean isClassResource();
	}

	private final List<String> messages = new ArrayList<String>();
	private final List<Path> paths = new ArrayList<>();
	private final List<String> classNames = new ArrayList<>();
	private long scantime = -1;
	private List<ClassLoader> classLoaders = new ArrayList<>();
	private List<String> beginResourceNameFilters = new ArrayList<>(0);
	private boolean filterOnlyResources = false;
	private boolean filterOnlyClasses = false;
	private Class<? extends Annotation> methodAnnotation;
	private Class<? extends Annotation> typeAnnotation;
	private boolean excludeInnerClasses = false;

	public ClasspathScanner() {

		// for java.lang
		final URL[] theUrls = Launcher.getBootstrapClassPath().getURLs();

		classLoaders.add(new URLClassLoader(theUrls) {
			public String toString() {
				return super.toString() + " [urls=" + Arrays.asList(theUrls) + "]";

			};
		});

		addSystemPropertyPaths("java.class.path");
	}

	/**
	 * Same as calling the constructor
	 * 
	 * @return
	 */
	public static ClasspathScanner create() {
		return new ClasspathScanner();
	}

	/**
	 * Inspects a {@link System}.getProperty by key and registers each path part
	 * as on the classpath
	 * 
	 * @param inKey
	 * @return
	 */
	public ClasspathScanner addSystemPropertyPaths(String inKey) {

		String value = System.getProperty(inKey);

		if (value == null || value.trim().isEmpty()) {
			return this;
		}

		for (String aPath : value.split("" + File.pathSeparatorChar, -1)) {

			if (aPath == null || aPath.trim().isEmpty()) {
				continue;
			}
			final File theFile = new File(aPath);
			if (!theFile.exists()) {
				continue;
			}

			try {

				final URL theUrl = theFile.toURI().toURL();

				classLoaders.add(new URLClassLoader(new URL[] { theUrl }) {

					public String toString() {

						return super.toString() + " [url=" + theUrl.toExternalForm() + "]";
					};

				});
			} catch (Exception e) {
				// swallow
			}
		}

		return this;
	}

	/**
	 * register a {@link Class} or better its {@link ClassLoader} to ensure to
	 * be in the scan.
	 * 
	 * @param inClass
	 * @return
	 */
	public ClasspathScanner addClass(Class<?> inClass) {

		if (inClass == null) {
			return this;
		}

		ClassLoader theCl = inClass.getClassLoader();
		while (theCl != null) {

			if (!classLoaders.contains(theCl)) {

				classLoaders.add(theCl);
			}

			theCl = (ClassLoader) theCl.getParent();
		}

		return this;

	}

	/**
	 * @return the {@link ClassLoader}s included into listing all the
	 *         {@link URL}s
	 */
	public List<ClassLoader> getClassLoaders() {

		return new ArrayList<>(classLoaders);
	}

	/**
	 * Actual scanning and filtering of all resources on the listed
	 * {@link ClassLoader}s
	 * 
	 * @param inHandler
	 * @return
	 */
	public ClasspathScanner scan(IHandler inHandler) {

		final long starttime = System.currentTimeMillis();

		reset();

		for (ClassLoader aClassLoader : classLoaders) {

			if (aClassLoader instanceof URLClassLoader) {

				scanByClassLoader(inHandler, (URLClassLoader) aClassLoader);

			} else {

				messages.add("unsupported ClassLaoder: " + aClassLoader);
			}
		}

		this.scantime = System.currentTimeMillis() - starttime;

		return this;
	}

	private void reset() {

		classNames.clear();
		paths.clear();
		messages.clear();
	}

	private void scanByClassLoader(IHandler inHandler, URLClassLoader inCl) {

		for (URL aUrl : inCl.getURLs()) {

			if (aUrl.getFile() != null && !aUrl.getFile().isEmpty()) {

				final File theFile = new File(aUrl.getFile());

				if (theFile.exists() && theFile.isDirectory()) {

					scanByDirectory(inHandler, inCl, aUrl, theFile);

				} else if (theFile.exists() && theFile.isFile() && theFile.getName().toLowerCase().endsWith(".jar")) {

					scanByJar(inHandler, inCl, aUrl, theFile);

				} else {

					messages.add("not found: " + aUrl);
				}

			}
		}
	}

	/**
	 * @return listing of messages during scanning
	 */
	public List<String> getMessages() {
		return new ArrayList<>(messages);
	}

	/**
	 * @return amount of scanned resources
	 */
	public int getResourcesCount() {
		return paths.size();
	}

	public long getScantime() {
		return scantime;
	}

	public int getClassesCount() {

		int i = 0;
		for (Path p : paths) {

			i += p.toString().endsWith(".class") ? 1 : 0;
		}

		return i;
	}

	private void scanByJar(IHandler inHandler, URLClassLoader inCl, URL aUrl, File inJarFile) {


		final List<Path> q = new ArrayList<>();

		try {
			
			final FileSystem theFs = FileSystems.newFileSystem(Paths.get(aUrl.toURI()), null);
			
			Files.walkFileTree(theFs.getRootDirectories().iterator().next(), new FileVisitor<Path>() {

				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {

					String theResourceName = file.toString().substring(1);
					scanByPath(inHandler, inCl, theResourceName, file);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (Exception e) {
			
			messages.add( "failed scanning jar: "+ inJarFile +": "+ e );
		}
	}

	private void scanByDirectory(IHandler inHandler, URLClassLoader inCl, URL aUrl, File inDirFile) {

		try {

			final File rootDir = inDirFile.getCanonicalFile();
			final int rootDirNameLen = rootDir.getCanonicalPath().length();

			Files.walkFileTree(rootDir.toPath(), new FileVisitor<Path>() {

				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {

					final String theResourcename = file.toFile().getCanonicalPath().substring(rootDirNameLen + 1);
					scanByPath(inHandler, inCl, theResourcename, file);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
					return FileVisitResult.CONTINUE;
				}
			});

		} catch (IOException e) {

			throw new RuntimeException(e);
		}
	}

	private void scanByPath(IHandler inHandler, final URLClassLoader inCl, final String inResourceName,
			final Path inPath) {

		paths.add(inPath);

		if (inHandler == null) {
			return;
		}

		final boolean isClassResource = inResourceName.toLowerCase().endsWith(".class");
		final String resourceName = !isClassResource ? inResourceName
				: inResourceName.substring(0, inResourceName.length() - 6).replace('/', '.').replace('\\', '.');

		if (filterOnlyClasses && !isClassResource) {
			return;
		}
		if (filterOnlyResources && isClassResource) {
			return;
		}
		if (typeAnnotation != null && !isClassResource) {
			return;
		}
		if (methodAnnotation != null && !isClassResource) {
			return;
		}
		if (excludeInnerClasses && isClassResource && resourceName.indexOf('$') > -1) {
			return;
		}

		for (String beginFilterName : beginResourceNameFilters) {
			if (!resourceName.startsWith(beginFilterName)) {
				return;
			}
		}

		final IResource theResource = new IResource() {

			@Override
			public ClassLoader getClassloader() {
				return inCl;
			}

			@Override
			public Path getPath() {
				return inPath;
			}

			@Override
			public String getResourceName() {
				return resourceName;
			}

			@Override
			public boolean isClassResource() {
				return isClassResource;
			}

			public String toString() {

				return IResource.class.getSimpleName() + " [resourceName=" + resourceName + ", path=" + inPath
						+ ", isClassResource=" + isClassResource + ", classLoader=" + inCl.getClass().getName() + "]";
			}

		};

		try {

			if (isClassResource && typeAnnotation != null) {

				final Class<?> theClass = inCl.loadClass(resourceName);

				if (theClass.getDeclaredAnnotation(typeAnnotation) == null) {

					return;
				}
			}

		} catch (Throwable e) {

			messages.add("failed to load class: " + e);
			return;
		}

		try {

			if (isClassResource && methodAnnotation != null) {

				final Class<?> theClass = inCl.loadClass(resourceName);

				boolean isFound = false;

				for (Method aMethod : theClass.getDeclaredMethods()) {

					System.out.println(aMethod);

					if (aMethod.getDeclaredAnnotation(methodAnnotation) != null) {
						isFound = true;
						break;
					}
				}

				if (isFound == false) {
					return;
				}
			}

		} catch (Throwable e) {

			messages.add("failed to scan methods at class " + resourceName + " : " + e);
			return;
		}

		inHandler.handle(theResource);
	}

	public ClasspathScanner filterBeginResourceName(String beginResourceName) {

		if (beginResourceName != null) {

			beginResourceNameFilters.add(beginResourceName);
		}

		return this;
	}

	/**
	 * @return handle only {@link Class}es
	 */
	public ClasspathScanner filterClassOnly() {

		filterOnlyClasses = true;
		filterOnlyResources = false;

		return this;
	}

	/**
	 * @return handle only resources and never {@link Class}es
	 */
	public ClasspathScanner filterResourceOnly() {

		filterOnlyClasses = false;
		filterOnlyResources = true;

		return this;
	}

	/**
	 * Let a scannable {@link Class} candidate have at least one declared method
	 * with the given {@link Annotation}
	 * 
	 * @param inAnnotationClass
	 * @return
	 */
	public ClasspathScanner filterMethodAnnotation(Class<? extends Annotation> inAnnotationClass) {

		methodAnnotation = inAnnotationClass;
		return this;
	}

	/**
	 * Let a scannable {@link Class} candidate have declare the given
	 * {@link Annotation}
	 * 
	 * @param inAnnotationClass
	 * @return
	 */
	public ClasspathScanner filterTypeAnnotation(Class<? extends Annotation> inAnnotationClass) {

		typeAnnotation = inAnnotationClass;
		return this;
	}

	/**
	 * Skip scanning on inner {@link Class}es
	 */
	public ClasspathScanner filterExcludeInnerClasses() {
		excludeInnerClasses = true;
		return this;
	}

	@Override
	public String toString() {
		return getClass().getName() + " [paths=" + paths.size() + ", scantime=" + scantime + "ms, messages="
				+ messages.size() + ", classLoaders=" + classLoaders + "]";
	}
}
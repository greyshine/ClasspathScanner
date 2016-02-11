package de.greyshine.utils;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
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

import com.sun.nio.zipfs.ZipPath;

import javax.management.RuntimeErrorException;

import sun.misc.Launcher;
import sun.misc.URLClassPath;

@SuppressWarnings({ "restriction", "unused" })
public class ClasspathScanner {

	private final List<String> messages = new ArrayList<String>();
	private final List<Path> paths = new ArrayList<>();
	private final List<String> classNames = new ArrayList<>();
	private long scantime = -1;
	private List<ClassLoader> classLoaders = new ArrayList<>();
	
	public ClasspathScanner() {
		
		// for java.lang
		final URL[] theUrls = Launcher.getBootstrapClassPath().getURLs(); 
		
		
		classLoaders.add( new URLClassLoader( theUrls ) { public String toString() {
			return super.toString()+" [urls="+ Arrays.asList( theUrls ) +"]";
			
		}; } );
		classLoaders.add( ClassLoader.getSystemClassLoader() );
		classLoaders.add( Thread.currentThread().getContextClassLoader() );
		
		addClass( getClass() );
		
		addSystemPropertyPaths("java.class.path");
		
		
	}
	
	public ClasspathScanner addSystemPropertyPaths(String inKey) {
		
		String value = System.getProperty( inKey );
		
		if ( value == null || value.trim().isEmpty() ) { return this; }
		
		for (String aPath : value.split( ""+File.pathSeparatorChar ,-1)) {

			if ( aPath == null || aPath.trim().isEmpty() ) { continue; }
			final File theFile = new File( aPath );
			if ( !theFile.exists() ) { continue; }
			
			try {
				
				final URL theUrl = theFile.toURI().toURL();
				
				classLoaders.add( new URLClassLoader( new URL[] { theUrl } ) {
					
					public String toString() {
						
						return super.toString() +" [url="+ theUrl.toExternalForm() +"]";
					};
					
				} );
			} catch (Exception e) {
				// swallow
			}
		}
		
		return this;
	}

	public ClasspathScanner addClass(Class<?> inClass) {
		
		if ( inClass == null ) { return this; }
		
		ClassLoader theCl = inClass.getClassLoader();
		while (theCl != null) {

			if ( !classLoaders.contains( theCl ) ) {
				
				classLoaders.add( theCl );
			}

			theCl = (ClassLoader) theCl.getParent();
		}

		return this;
		
	}
	
	public List<ClassLoader> getClassLoaders() {
		
		return new ArrayList<>( classLoaders );
	}
	
	@Override
	public String toString() {
		return getClass().getName() +" [paths="+ paths.size() +", scantime="+ scantime +"ms, messages="+ messages.size() +", classLoaders="+ classLoaders +"]";
	}

	public ClasspathScanner scan(IHandler inHandler) {

		final long starttime = System.currentTimeMillis();
		
		reset();

		for (ClassLoader aClassLoader : classLoaders) {
			
			if ( aClassLoader instanceof URLClassLoader ) {
				
				scanByClassLoader(inHandler, (URLClassLoader)aClassLoader);
			
			} else {
			
				messages.add( "unsupported ClassLaoder: "+ aClassLoader );
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

	public List<String> getMessages() {
		return new ArrayList<>(messages);
	}

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

		try {

			final FileSystem theFs = FileSystems.newFileSystem(Paths.get(aUrl.toURI()), null);

			final List<Path> q = new ArrayList<>();

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

			throw new RuntimeException(e);
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
		
		if ( inHandler == null ) { return; }

		inHandler.handle(new IResource() {
			
			final boolean isClassResource = inResourceName.toLowerCase().endsWith( ".class" );
			final String resourceName;
			
			{
				resourceName = !isClassResource ? inResourceName : inResourceName.substring(0, inResourceName.length() - 6).replace('/', '.').replace('\\', '.');
			}

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
				
				return IResource.class.getSimpleName()+" [resourceName="+ resourceName +", path="+ inPath +", isClassResource="+ isClassResource +", classLoader="+ inCl.getClass().getName() +"]";
			}
			
		});

	}

	public static interface IHandler {
		void handle(IResource inResource);
	}

	public static interface IResource {
		ClassLoader getClassloader();

		Path getPath();

		String getResourceName();

		boolean isClassResource();
	}

}
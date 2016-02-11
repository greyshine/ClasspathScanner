package de.greyshine.utils;

import de.greyshine.utils.ClasspathScanner.IHandler;
import de.greyshine.utils.ClasspathScanner.IResource;

public class Main {

	public static void main(String[] args) {

		ClasspathScanner.create()
			.filterBeginResourceName("de.greyshine.utils.Main")
			.filterExcludeInnerClasses()
			.scan(new IHandler() {

					@Override
					public void handle(IResource inResource) {

						try {

							final Class<?> theClass = inResource.getClassloader()
									.loadClass(inResource.getResourceName());
							System.out.println("Hello " + inResource.getResourceName() + " as " + theClass + "!");

						} catch (ClassNotFoundException e) {

							throw new RuntimeException(e);
						}
					}
				});
	}

}

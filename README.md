# ClasspathScanner
Scanning and filtering the Java classpath.

Goal: One class. No dependencies.

Features:

* filter by class annotation
* filter by method annotation
* filter not to have subclasses
* filter only classes
* filter only resources (not classes)

```java

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
```


run
`mvn clean test && java -cp target/test-classes:target/classes de.greyshine.utils.Main`
which prints out
`Hello de.greyshine.utils.Main as class de.greyshine.utils.Main!`



 

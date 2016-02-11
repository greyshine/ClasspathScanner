package de.greyshine.utils;

import org.junit.Assert;
import org.junit.Test;

import de.greyshine.utils.ClasspathScanner.IHandler;
import de.greyshine.utils.ClasspathScanner.IResource;

@TestAnnotation
public class ClasspathScannerTest {

	@Test
	public void testAll() {

		final Wrapper<Integer> cnt = new Wrapper<>(0);
		final Wrapper<Boolean> isFoundMe = new Wrapper<>(false);
		final Wrapper<Boolean> isFoundJavaClass = new Wrapper<>(false);

		final ClasspathScanner cps = new ClasspathScanner().scan(new ClasspathScanner.IHandler() {

			@Override
			public void handle(IResource inResource) {

				Assert.assertNotNull("resource is null", inResource);

				if (inResource.isClassResource() && "java.lang.Class".equals(inResource.getResourceName())) {

					isFoundJavaClass.value = true;

				} else if (inResource.isClassResource()) {

					Assert.assertFalse("bad resourcename: " + inResource.getResourceName(),
							inResource.getResourceName().toLowerCase().endsWith(".class"));
					Assert.assertTrue("bad resourcename: " + inResource.getResourceName(),
							inResource.getResourceName().indexOf('/') == -1);
					Assert.assertTrue("bad resourcename: " + inResource.getResourceName(),
							inResource.getResourceName().indexOf('\\') == -1);
				}

				cnt.value++;

				if (inResource.isClassResource()
						&& inResource.getResourceName().equals(ClasspathScanner.class.getName())) {

					isFoundMe.value = true;
				}

			}
		});

		System.out.println("\n\n");
		System.out.println(cps);
		System.out.println("cnt: " + cnt.value);
		System.out.println("foundme: " + isFoundMe.value);
		System.out.println("java.Class: " + isFoundJavaClass.value);
		System.out.println("msgs: " + cps.getMessages());

		Assert.assertTrue("nothing found", cnt.value > 0);
		Assert.assertTrue("Myself not found", isFoundMe.value);
		Assert.assertTrue("java.lang.Class not found", isFoundJavaClass.value);

	}

	@Test
	public void testListGreyshineClasses() {

		final ClasspathScanner cps = ClasspathScanner.create(); 
		
		cps.filterBeginResourceName("de.greyshine");
		cps.filterClassOnly();

		Wrapper<Integer> c = new Wrapper<>(0);

		cps.scan(new IHandler() {

			@Override
			public void handle(IResource inResource) {

				Assert.assertTrue(inResource.toString(), inResource.isClassResource());
				Assert.assertTrue(inResource.toString(), inResource.getResourceName().startsWith("de.greyshine"));
				c.value++;

				System.out.println(inResource);
			}
		});

		Assert.assertTrue(c.value > 0);
	}

	@Test
	public void testTypeAnnotation() {
		
		final ClasspathScanner cps = ClasspathScanner.create();
		cps.filterBeginResourceName("de.greyshine.utils");
		cps.filterExcludeInnerClasses();
		cps.filterTypeAnnotation(TestAnnotation.class);

		Wrapper<Integer> c = new Wrapper<>(0);

		cps.scan(new IHandler() {

			@Override
			public void handle(IResource inResource) {

				Assert.assertTrue(inResource.isClassResource());
				Assert.assertEquals(ClasspathScannerTest.class.getName(), inResource.getResourceName());
				c.value++;
			}
		});

		Assert.assertTrue("" + c, c.value > 0);
	}

	@Test
	public void testMethodAnnotation() {
		
		final ClasspathScanner cps = ClasspathScanner.create();
		cps.filterBeginResourceName("de.greyshine.utils");
		cps.filterExcludeInnerClasses();
		cps.filterMethodAnnotation(Test.class);

		Wrapper<Integer> c = new Wrapper<>(0);

		cps.scan(new IHandler() {

			@Override
			public void handle(IResource inResource) {

				Assert.assertTrue(inResource.isClassResource());
				Assert.assertEquals(ClasspathScannerTest.class.getName(), inResource.getResourceName());
				c.value++;
			}
		});

		Assert.assertTrue(c.value > 0);
	}

}

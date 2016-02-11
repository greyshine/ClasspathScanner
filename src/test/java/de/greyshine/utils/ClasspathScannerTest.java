package de.greyshine.utils;

import java.util.Enumeration;
import java.util.Properties;

import org.junit.Assert;
import org.junit.Test;

import de.greyshine.utils.ClasspathScanner.IResource;

public class ClasspathScannerTest {
	
	final ClasspathScanner cps = new ClasspathScanner();
	
	@Test
	public void print() {
		
		
		
		System.out.println( "-------------" );
		System.out.println( getClass().getClassLoader() );
		System.out.println( ClassLoader.getSystemClassLoader() );
		System.out.println( Thread.currentThread().getContextClassLoader() );
		
		final Enumeration<String> e 
		= (Enumeration<String>) System.getProperties().propertyNames();
		
		while( e.hasMoreElements() ) {
			
			String k = e.nextElement();
			//System.out.println( k +" = "+ System.getProperty( k ) );
		}
		
		
		
		
		
		System.out.println( "-------------" );
		
		
	}
	
	@Test
	public void test() {
		
		final Wrapper<Integer> cnt = new Wrapper<>(0);
		final Wrapper<Boolean> isFoundMe = new Wrapper<>(false);
		final Wrapper<Boolean> isFoundJavaClass = new Wrapper<>(false);
		
		cps.scan( new ClasspathScanner.IHandler() {
			
			@Override
			public void handle( IResource inResource ) {
				
				Assert.assertNotNull( "resource is null", inResource);
				
				if ( inResource.isClassResource() && "java.lang.Class".equals( inResource.getResourceName() ) ) {
					
					isFoundJavaClass.value = true;
					
				} else
				if ( inResource.isClassResource() ) {
					
					Assert.assertFalse( "bad resourcename: "+ inResource.getResourceName() , inResource.getResourceName().toLowerCase().endsWith( ".class" ));
					Assert.assertTrue( "bad resourcename: "+ inResource.getResourceName() , inResource.getResourceName().indexOf( '/' ) == -1);
					Assert.assertTrue( "bad resourcename: "+ inResource.getResourceName() , inResource.getResourceName().indexOf( '\\' ) == -1);
				}
				
				cnt.value++;
				
				if ( inResource.isClassResource() && inResource.getResourceName().equals( ClasspathScanner.class.getName() ) ) {
					
					isFoundMe.value = true;
				}
				
				if ( inResource.getResourceName().startsWith( "de.grey" ) ) {
					System.out.println( "OK: "+ inResource );
				}
				if ( inResource.getResourceName().startsWith( "java.lang" ) ) {
					//System.out.println( inResource );
				}
				
			}
		} );
		
		
		
		System.out.println( "\n\n" );
		System.out.println( cps );
		System.out.println( "cnt: "+ cnt.value );
		System.out.println( "foundme: "+isFoundMe.value );
		System.out.println( "java.Class: "+ isFoundJavaClass.value );
		System.out.println( "msgs: "+ cps.getMessages() );
		
		Assert.assertTrue( "nothing found", cnt.value > 0 );
		Assert.assertTrue( "Myself not found", isFoundMe.value );
		
	}
	
	static class Wrapper<T> {
		
		T value;
		
		Wrapper() {}
		Wrapper(T v) {
			value = v;
		}
		
		
	}

}

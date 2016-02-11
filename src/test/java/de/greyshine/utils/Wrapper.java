package de.greyshine.utils;

public class Wrapper<T> {
	
	public T value;
	
	public Wrapper(T inValue) {
		value = inValue;
	}

	@Override
	public String toString() {
		return String.valueOf( value );
	}

}

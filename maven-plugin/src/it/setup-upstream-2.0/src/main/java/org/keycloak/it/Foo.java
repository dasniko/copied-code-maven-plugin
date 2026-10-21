package org.keycloak.it;

public class Foo {

	public static final int LIMIT = 10;

	/**
	 * Adds two numbers.
	 */
	public int add(int a,
				   int b) {
		// only reformatted and commented
		return a + b;
	}

	public String greet(String name) {
		return "Hello, " + name + "!";
	}
}

package de.example;

import dasniko.copiedcode.CopiedFrom;
import dasniko.copiedcode.CopiedFromKeycloak;

public class Local {

	// UNCHANGED, artifact auto-detected
	@CopiedFromKeycloak(source = "org.keycloak.it.Foo#LIMIT", version = "1.0")
	static final int LIMIT = 10;

	// UNCHANGED: upstream only reformatted and commented
	@CopiedFrom(artifact = "org.keycloak:it-fake-upstream", source = "org.keycloak.it.Foo", version = "1.0")
	public int add(int a, int b) {
		return a + b;
	}

	// CHANGED, although the local copy is modified
	@CopiedFromKeycloak(source = "org.keycloak.it.Foo", version = "1.0", modified = true, changes = "trims the name")
	public String greet(String name) {
		return "Hello " + name.trim();
	}

	// REMOVED
	@CopiedFromKeycloak(artifact = "it-fake-upstream", source = "org.keycloak.it.Foo#gone()", version = "1.0")
	public void gone() {
	}

	// UNRESOLVABLE: baseline version does not exist
	@CopiedFromKeycloak(artifact = "it-fake-upstream", source = "org.keycloak.it.Foo#add(int, int)", version = "0.9")
	public int sum(int a, int b) {
		return a + b;
	}
}

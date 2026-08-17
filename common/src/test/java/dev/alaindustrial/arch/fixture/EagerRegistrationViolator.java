package dev.alaindustrial.arch.fixture;

/**
 * Deliberate violator for {@code ArchitectureRules.notCallFromStaticInitializer("Registry",
 * "register")} (MOD-435): a {@code static final} field initializer, which javac compiles into
 * {@code <clinit>} — the exact shape that throws {@code already frozen} on NeoForge.
 */
public final class EagerRegistrationViolator {
	static final Object EAGER = Registry.register("alaindustrial:eager", new Object());

	private EagerRegistrationViolator() {
	}
}

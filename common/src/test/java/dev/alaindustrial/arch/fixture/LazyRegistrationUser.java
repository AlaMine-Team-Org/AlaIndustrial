package dev.alaindustrial.arch.fixture;

/**
 * Clean counterpart of {@link EagerRegistrationViolator} (MOD-435): the same {@code Registry.register}
 * call, but from an ordinary method a loader entrypoint would invoke at the right moment — which the
 * rule allows ({@code ModRecipes}, {@code ModCriteria} register that way). The negative control asserts
 * this class is absent from the report.
 */
public final class LazyRegistrationUser {
	private LazyRegistrationUser() {
	}

	static Object registerLater() {
		return Registry.register("alaindustrial:lazy", new Object());
	}
}

package dev.alaindustrial.arch.fixture;

/**
 * Stand-in for the registry the eager-registration rule guards (MOD-435).
 *
 * <p>{@code ArchitectureRules.notCallFromStaticInitializer("Registry", "register")} matches the
 * call target by SIMPLE name, and {@code net.minecraft.core.Registry} is not on {@code :common}'s
 * test classpath (verified: {@code :common:dependencies --configuration testCompileClasspath} lists
 * no Minecraft artifact). So the fixture supplies its own class of that simple name — deliberately
 * in this package, never a fake {@code net.minecraft.*} package. The negative control therefore
 * proves the condition's shape (a {@code <clinit>} call to something named {@code Registry.register}),
 * which is exactly the property the production rule relies on.
 */
public final class Registry {
	private Registry() {
	}

	public static <T> T register(String id, T entry) {
		return entry;
	}
}

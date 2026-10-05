package dev.alaindustrial.arch.fixture;

/**
 * Stand-in for {@code dev.alaindustrial.gametest.ConfigOverrides} (MOD-710). The real class takes a
 * Minecraft {@code GameTestHelper}, which {@code :common}'s test classpath does not carry, so a fixture
 * cannot call it; {@code ArchitectureRules.callForTestOutside} takes the owner's name as a parameter and
 * the negative control points it here. Same shape: a static {@code forTest}.
 */
public final class ForTestHolder {

	private ForTestHolder() {
	}

	public static ForTestHolder forTest(Object helper) {
		return new ForTestHolder();
	}
}

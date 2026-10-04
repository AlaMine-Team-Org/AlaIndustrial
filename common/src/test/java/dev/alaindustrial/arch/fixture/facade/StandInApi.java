package dev.alaindustrial.arch.fixture.facade;

/**
 * Stand-in for a Minecraft class with a member that differs between the game lines (MOD-703, batch 13): the
 * real owners ({@code PoseStack}, {@code BonemealableBlock}, {@code Player}) are not on {@code :common}'s test
 * classpath, so {@code ArchitectureRules.callAFacadeOnlyMember} is aimed here by the negative control.
 * {@code turn(Quaternion)} plays {@code PoseStack.rotate(Quaternionfc)} — listed for one overload only —
 * {@code turn(Matrix)} the overload both lines share, {@code grow()} a member listed by name alone.
 */
public class StandInApi {

	/** Plays {@code Quaternionfc}: the first parameter that makes an overload facade-only. */
	public static final class Quaternion {
	}

	/** Plays {@code Matrix4fc}: an overload the lines share. */
	public static final class Matrix {
	}

	public void turn(Quaternion rotation) {
	}

	public void turn(Matrix transform) {
	}

	public void grow() {
	}
}

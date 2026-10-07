package dev.alaindustrial.arch.fixture.sheet;

/**
 * Stand-in for {@code net.minecraft.client.renderer.Sheets} (MOD-780): the real class is not on {@code :common}'s
 * test classpath, so {@code TranslucentSheetRules.callTheDepthWritingSheet} is aimed here by the negative
 * control. {@code translucentBlockItemSheet()} plays the banned depth-writing sheet, {@code cutoutBlockItemSheet()}
 * a sheet the rule leaves alone.
 */
public final class StandInSheets {

	private StandInSheets() {
	}

	public static Object translucentBlockItemSheet() {
		return new Object();
	}

	public static Object cutoutBlockItemSheet() {
		return new Object();
	}
}

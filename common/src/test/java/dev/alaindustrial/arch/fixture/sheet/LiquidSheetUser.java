package dev.alaindustrial.arch.fixture.sheet;

import java.util.function.Function;

/**
 * Clean twin for {@code TranslucentSheetRules.callTheDepthWritingSheet} (MOD-780): {@code submitLiquid} is listed
 * as an allowed site by the negative control and calls the depth-writing sheet directly, the way the three
 * liquid sites do; the cutout sheet is called anywhere, inside a lambda too. Nothing here may be reported.
 */
public final class LiquidSheetUser {

	static final Object FRAME_TYPE = renderType(ignored -> StandInSheets.cutoutBlockItemSheet());

	private LiquidSheetUser() {
	}

	static Object submitLiquid() {
		return StandInSheets.translucentBlockItemSheet();
	}

	private static Object renderType(Function<String, Object> sheet) {
		return sheet.apply("blocks");
	}
}

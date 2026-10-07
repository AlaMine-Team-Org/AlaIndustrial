package dev.alaindustrial.arch.fixture.sheet;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Deliberate violator for {@code TranslucentSheetRules.callTheDepthWritingSheet} (MOD-780): the three shapes in
 * which a renderer reaches the depth-writing sheet — a direct call, a call inside a lambda (how every renderer
 * picks a sprite's render type, {@code SPRITE.renderType(ignored -> Sheets.translucentBlockItemSheet())}) and a
 * method reference.
 */
public final class DepthWritingSheetViolator {

	static final Object GLASS_TYPE = renderType(ignored -> StandInSheets.translucentBlockItemSheet());

	private DepthWritingSheetViolator() {
	}

	static Object direct() {
		return StandInSheets.translucentBlockItemSheet();
	}

	static Supplier<Object> reference() {
		return StandInSheets::translucentBlockItemSheet;
	}

	private static Object renderType(Function<String, Object> sheet) {
		return sheet.apply("blocks");
	}
}

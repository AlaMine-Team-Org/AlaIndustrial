package dev.alaindustrial.client.blueprint;

import java.util.Optional;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The "Blueprint" button on a guide-book page (MOD-797): shown when the page's block has a blueprint,
 * it opens the sheet and closing the sheet returns to the book.
 */
public final class BlueprintGuideButton {

	private static final int WIDTH = 70;

	private BlueprintGuideButton() {
	}

	/**
	 * The button for the page of item {@code itemId}, right-aligned at {@code right}, or empty when that
	 * item has no blueprint.
	 */
	public static Optional<Button> create(String itemId, Screen book, int right, int top, int height) {
		return BlueprintCatalog.forSubject(itemId).map(def -> {
			Component label = Component.translatable("blueprint.alaindustrial.guide_button");
			if (!BlueprintProgress.isViewed(def.id())) {
				label = Component.literal("★ ").append(label);
			}
			return Button.builder(label, b -> BlueprintInput.open(def, book))
					.bounds(right - WIDTH, top, WIDTH, height).build();
		});
	}
}

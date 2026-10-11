package dev.alaindustrial.client.blueprint;

import dev.alaindustrial.client.ModKeyMappings;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The tooltip line that leads to a blueprint (MOD-797), and the memory of which item's tooltip is on
 * screen right now. Every tooltip — inventory, creative tab, JEI, REI — goes through the loaders' tooltip
 * hooks into {@code MachineTooltips.append}, so remembering the last item seen there is what lets the
 * blueprint key work over any of them without one hook per recipe viewer.
 */
public final class BlueprintTooltip {

	/** A tooltip counts as "on screen" for this long after it was last built, in milliseconds. */
	private static final long FRESH_MS = 250;
	/** Segments of the hold-to-open bar. */
	private static final int BAR = 24;

	private static @Nullable BlueprintDef hovered;
	private static long hoveredAt;

	private BlueprintTooltip() {
	}

	/**
	 * Add the "[I] Blueprint · steps: 4" line when {@code stack} has a blueprint: bright with a NEW stamp until
	 * the sheet has been opened, dark grey after; a filling bar while the key is being held.
	 */
	public static void append(ItemStack stack, List<Component> lines) {
		Optional<BlueprintDef> found = forStack(stack);
		// No client (a server-side test of the tooltip text): there is no key to show and no sheet to open.
		if (found.isEmpty() || Minecraft.getInstance() == null) {
			return;
		}
		BlueprintDef def = found.get();
		if (!(Minecraft.getInstance().gui.screen() instanceof BlueprintScreen)) {
			hovered = def;
			hoveredAt = System.currentTimeMillis();
		}
		float held = BlueprintHold.progress(def);
		if (held > 0f) {
			lines.add(progressLine(held));
			return;
		}
		boolean viewed = BlueprintProgress.isViewed(def.id());
		Component line = Component.translatable("blueprint.alaindustrial.tooltip",
				ModKeyMappings.OPEN_BLUEPRINT.getTranslatedKeyMessage(), def.steps().size())
				.withStyle(viewed ? ChatFormatting.DARK_GRAY : ChatFormatting.AQUA);
		if (!viewed) {
			line = Component.empty().append(line).append(" ")
					.append(Component.translatable("blueprint.alaindustrial.tooltip.new")
							.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		}
		lines.add(line);
	}

	/** The hold-to-open bar: "Blueprint ||||||......" filling as the key is held. */
	private static Component progressLine(float held) {
		int filled = Math.round(held * BAR);
		return Component.translatable("blueprint.alaindustrial.tooltip.opening").withStyle(ChatFormatting.AQUA)
				.append(" ")
				.append(Component.literal("|".repeat(filled)).withStyle(ChatFormatting.AQUA))
				.append(Component.literal("|".repeat(BAR - filled)).withStyle(ChatFormatting.DARK_GRAY));
	}

	/** The blueprint of the item whose tooltip is on screen, if one was built just now. */
	static Optional<BlueprintDef> hoveredNow() {
		if (hovered == null || System.currentTimeMillis() - hoveredAt > FRESH_MS) {
			return Optional.empty();
		}
		return Optional.of(hovered);
	}

	/** The blueprint {@code stack}'s item leads to, if any. */
	public static Optional<BlueprintDef> forStack(ItemStack stack) {
		if (stack.isEmpty()) {
			return Optional.empty();
		}
		return BlueprintCatalog.forSubject(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
	}
}

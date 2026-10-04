package dev.alaindustrial.item.tool;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SmithingTemplateItem;

/**
 * Netherite Drill Upgrade smithing template (MOD-534) — the mod's first smithing template, and the gate on its top
 * drill tier. Built on vanilla's own {@link SmithingTemplateItem} rather than a plain Item so the smithing screen
 * shows what goes in which slot and greys the empty slots with the right sprites, exactly as it does for the
 * vanilla netherite upgrade. Constructor argument order was read off the bytecode of
 * {@code Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE}'s factory (project rule 1): appliesTo, ingredients,
 * baseSlotDescription, additionsSlotDescription, then the two icon lists.
 *
 * <p>This class only builds the item. The Materials domain of the content manifest declares the entry
 * ({@code item("netherite_drill_upgrade_smithing_template", NetheriteDrillUpgradeTemplate::create, …)}); the
 * construction moved here so the declarative table stays a table (MOD-711).
 */
public final class NetheriteDrillUpgradeTemplate {
	private NetheriteDrillUpgradeTemplate() {
	}

	/** The template item for the loader-supplied {@code properties}. */
	public static SmithingTemplateItem create(Item.Properties properties) {
		return new SmithingTemplateItem(
				Component.translatable("item.alaindustrial.netherite_drill_upgrade_smithing_template.applies_to")
						.withStyle(ChatFormatting.BLUE),
				Component.translatable("item.alaindustrial.netherite_drill_upgrade_smithing_template.ingredients")
						.withStyle(ChatFormatting.BLUE),
				Component.translatable(
						"item.alaindustrial.netherite_drill_upgrade_smithing_template.base_slot_description"),
				Component.translatable(
						"item.alaindustrial.netherite_drill_upgrade_smithing_template.additions_slot_description"),
				List.of(Identifier.withDefaultNamespace("container/slot/pickaxe")),
				List.of(Identifier.withDefaultNamespace("container/slot/ingot")),
				properties);
	}
}

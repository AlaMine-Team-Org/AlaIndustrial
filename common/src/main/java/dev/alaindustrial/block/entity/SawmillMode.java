package dev.alaindustrial.block.entity;

import dev.alaindustrial.registry.ModRecipes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The four cutting modes of the {@link SawmillBlockEntity} (MOD-150). Each mode is backed by its own
 * {@link ModRecipes.Kind} recipe family, so the machine saws the same log into planks, sticks, slabs
 * or stairs depending on which button the player has selected. The active mode is persisted in NBT
 * and synced to the open screen via a dedicated {@link net.minecraft.world.inventory.ContainerData}
 * index; the recipe set for the other three modes is inert while a mode is active.
 *
 * <p>The {@link #icon} is a vanilla representative item drawn as a ghost on the GUI mode button — it
 * is purely cosmetic (oak variants stand in for "planks/sticks/slabs/stairs" in general); the actual
 * per-species output comes from the matched recipe, not from this icon.
 */
public enum SawmillMode {
	PLANKS(ModRecipes.SAWING_PLANKS, Items.OAK_PLANKS),
	STICKS(ModRecipes.SAWING_STICKS, Items.STICK),
	SLABS(ModRecipes.SAWING_SLABS, Items.OAK_SLAB),
	STAIRS(ModRecipes.SAWING_STAIRS, Items.OAK_STAIRS);

	private static final SawmillMode[] VALUES = values();

	private final ModRecipes.Kind kind;
	private final Item icon;

	SawmillMode(ModRecipes.Kind kind, Item icon) {
		this.kind = kind;
		this.icon = icon;
	}

	/** The recipe family this mode saws with. */
	public ModRecipes.Kind kind() {
		return kind;
	}

	/** Ghost icon drawn on this mode's GUI button (cosmetic representative item). */
	public ItemStack iconStack() {
		return new ItemStack(icon);
	}

	/** Lang key suffix for the button tooltip: {@code gui.alaindustrial.sawmill.mode.<name>}. */
	public String translationKey() {
		return "gui.alaindustrial.sawmill.mode." + name().toLowerCase(java.util.Locale.ROOT);
	}

	/** Ordinal-safe lookup used when decoding a synced/persisted mode; out-of-range falls back to {@link #PLANKS}. */
	public static SawmillMode byOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : PLANKS;
	}
}

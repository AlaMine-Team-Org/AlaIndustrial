package dev.alaindustrial.block.entity;

import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModRecipes;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * The cutting modes of the {@link SawmillBlockEntity} (MOD-150; the fifth, sawdust, MOD-655). Each mode is
 * backed by its own {@link ModRecipes.Kind} recipe family, so the machine saws the same log into planks, sticks,
 * slabs, stairs or sawdust depending on which button the player has selected. The active mode is persisted in NBT
 * and synced to the open screen via a dedicated {@link net.minecraft.world.inventory.ContainerData}
 * index; the recipe sets of the other modes are inert while a mode is active.
 *
 * <p><b>The order is a save format.</b> The ordinal is what the block entity writes as {@code "Mode"} and what
 * the GUI channel carries, so a new mode is appended LAST: inserting one would turn every saved sawmill into a
 * different mode on load.
 *
 * <p>The {@link #icon} is a representative item drawn as a ghost on the GUI mode button — it is purely
 * cosmetic (oak variants stand in for "planks/sticks/slabs/stairs" in general); the actual per-species output
 * comes from the matched recipe, not from this icon. A supplier, because the sawdust is the mod's own item and is
 * bound only when the loader registers it — after this enum may already have been initialised.
 */
public enum SawmillMode {
	PLANKS(ModRecipes.SAWING_PLANKS, () -> Items.OAK_PLANKS),
	STICKS(ModRecipes.SAWING_STICKS, () -> Items.STICK),
	SLABS(ModRecipes.SAWING_SLABS, () -> Items.OAK_SLAB),
	STAIRS(ModRecipes.SAWING_STAIRS, () -> Items.OAK_STAIRS),
	// MOD-655: appended last — the ordinal is the saved "Mode".
	SAWDUST(ModRecipes.SAWING_SAWDUST, () -> ModContent.SAWDUST.get());

	private static final SawmillMode[] VALUES = values();

	private final ModRecipes.Kind kind;
	private final Supplier<Item> icon;

	SawmillMode(ModRecipes.Kind kind, Supplier<Item> icon) {
		this.kind = kind;
		this.icon = icon;
	}

	/** The recipe family this mode saws with. */
	public ModRecipes.Kind kind() {
		return kind;
	}

	/** Ghost icon drawn on this mode's GUI button (cosmetic representative item). */
	public ItemStack iconStack() {
		return new ItemStack(icon.get());
	}

	/** Lang key suffix for the button tooltip: {@code gui.alaindustrial.sawmill.mode.<name>}. */
	public String translationKey() {
		return "gui.alaindustrial.sawmill.mode." + name().toLowerCase(java.util.Locale.ROOT);
	}

	/**
	 * The mode that works this recipe family, or {@code null} for a family the sawmill does not saw.
	 * Used to name that family's recipe-viewer category — see
	 * {@link dev.alaindustrial.client.compat.RecipeCategoryTitle}, which owns the composition so the
	 * sawmill and the incubator cannot title their tabs two different ways.
	 */
	@Nullable
	public static SawmillMode forKind(ModRecipes.Kind kind) {
		for (SawmillMode mode : VALUES) {
			if (mode.kind == kind) {
				return mode;
			}
		}
		return null;
	}

	/** Ordinal-safe lookup used when decoding a synced/persisted mode; out-of-range falls back to {@link #PLANKS}. */
	public static SawmillMode byOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : PLANKS;
	}
}

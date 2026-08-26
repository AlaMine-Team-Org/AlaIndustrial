package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModRecipes;
import java.util.Locale;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The incubator's mutation modes (MOD-118), one per mutation chip.
 *
 * <p>Unlike the sawmill — where the mode is a button and a stored field — the incubator derives its
 * mode from the chip sitting in the chip slot. There is nothing to persist: the chip is part of the
 * inventory, so the mode survives a reload for free, and automation can switch it by swapping chips.
 */
public enum IncubatorMode {
	/** A → B, the cheapest and most reliable operation. */
	TRANSFORM(ModRecipes.MUTATION_TRANSFORM),
	/** 1 item → 2 of the same. */
	DUPLICATE(ModRecipes.MUTATION_DUPLICATE),
	/** An ordinary item → a unique mod material; the priciest operation in the mod. */
	CREATE(ModRecipes.MUTATION_CREATE);

	private static final IncubatorMode[] VALUES = values();

	private final ModRecipes.Kind kind;

	IncubatorMode(ModRecipes.Kind kind) {
		this.kind = kind;
	}

	public ModRecipes.Kind kind() {
		return kind;
	}

	/** Ticks one attempt takes in this mode, before the global speed multiplier. */
	public int baseDuration() {
		return switch (this) {
			case TRANSFORM -> Config.mutationDurationTransform;
			case DUPLICATE -> Config.mutationDurationDuplicate;
			case CREATE -> Config.mutationDurationCreate;
		};
	}

	/** Default success chance of this mode; an individual recipe may override it. */
	public double baseChance() {
		return switch (this) {
			case TRANSFORM -> Config.mutationChanceTransform;
			case DUPLICATE -> Config.mutationChanceDuplicate;
			case CREATE -> Config.mutationChanceCreate;
		};
	}

	public String translationKey() {
		return "gui.alaindustrial.incubator.mode." + name().toLowerCase(Locale.ROOT);
	}


	/** The mode the given chip selects, or {@code null} when the stack is not a mutation chip. */
	@Nullable
	public static IncubatorMode forChip(ItemStack chip) {
		if (chip.isEmpty()) {
			return null;
		}
		if (chip.is(ModContent.MUTATION_CHIP_TRANSFORM.get())) {
			return TRANSFORM;
		}
		if (chip.is(ModContent.MUTATION_CHIP_DUPLICATE.get())) {
			return DUPLICATE;
		}
		if (chip.is(ModContent.MUTATION_CHIP_CREATE.get())) {
			return CREATE;
		}
		return null;
	}

	/** The mode that works this recipe family, or {@code null} for a family the incubator does not run. */
	@Nullable
	public static IncubatorMode forKind(ModRecipes.Kind kind) {
		for (IncubatorMode mode : VALUES) {
			if (mode.kind == kind) {
				return mode;
			}
		}
		return null;
	}

	/**
	 * Success chance a recipe of this family actually runs at: its own {@code chance} when it states
	 * one (the duplicate value classes do), otherwise the mode default. Returns 0 for a family no mode
	 * works, which is how the recipe viewers tell "not a gamble" from "45 %".
	 */
	public static double chanceOf(ModRecipes.Kind kind, double recipeChance) {
		IncubatorMode mode = forKind(kind);
		if (mode == null) {
			return 0.0;
		}
		return recipeChance >= 0 ? recipeChance : mode.baseChance();
	}

	public static IncubatorMode byOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : TRANSFORM;
	}

	/** ContainerData encodes "no chip" as -1; this maps it back for the screen. */
	@Nullable
	public static IncubatorMode byDataValue(int value) {
		return value < 0 ? null : byOrdinal(value);
	}
}

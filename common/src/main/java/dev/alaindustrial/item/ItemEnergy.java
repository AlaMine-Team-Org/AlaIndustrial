package dev.alaindustrial.item;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.registry.ModDataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Neutral EU buffer for items (MOD-052) — the item-side counterpart of the block-side energy core,
 * and the shared foundation for future powered items (electric tools / armor,
 * {@code docs/FUTURE_CONTENT.md}). Charge lives in the {@code alaindustrial:pouch_energy} data
 * component ({@code Long}); this helper owns the read/write/clamp rules so items never touch the
 * component directly.
 *
 * <p>Conventions:
 * <ul>
 * <li>An absent component reads as 0 EU, and writing 0 removes the component — a drained item and a
 * freshly crafted one are component-identical (no "same-looking but unequal" stacks).</li>
 * <li>Values are clamped to {@code [0, capacity(stack)]} on every write.</li>
 * <li>{@link #capacity} resolves per item type; non-powered items report 0 and ignore writes.</li>
 * </ul>
 */
public final class ItemEnergy {
	private ItemEnergy() {
	}

	/** Max EU the item can hold; 0 for items without an energy buffer. */
	public static long capacity(ItemStack stack) {
		if (stack.getItem() instanceof PouchItem) {
			return Config.lvPouchBuffer;
		}
		if (stack.getItem() instanceof EnergyPackItem) {
			return Config.energyPackBuffer;
		}
		if (stack.getItem() instanceof ElectricDrillItem) {
			return Config.electricDrillBuffer;
		}
		if (stack.getItem() instanceof MagnetItem) {
			return Config.magnetBuffer;
		}
		if (stack.getItem() instanceof JetpackItem) {
			return Config.jetpackBuffer;
		}
		return 0L;
	}

	/**
	 * Max EU/tick this item accepts while sitting in a charge slot. A charger caps its transfer at
	 * {@code min(its own tier ceiling, inputRate(stack))} — the item's own ceiling, so a small pouch
	 * cannot be force-fed at a big charger's rate. 0 for items without a buffer.
	 */
	public static long inputRate(ItemStack stack) {
		if (stack.getItem() instanceof PouchItem) {
			return EnergyTier.LV.maxVoltage();
		}
		if (stack.getItem() instanceof EnergyPackItem) {
			return Config.energyPackInputRate;
		}
		if (stack.getItem() instanceof ElectricDrillItem) {
			return Config.electricDrillInputRate;
		}
		if (stack.getItem() instanceof MagnetItem) {
			return Config.magnetInputRate;
		}
		if (stack.getItem() instanceof JetpackItem) {
			return Config.jetpackInputRate;
		}
		return 0L;
	}

	/** Stored EU (absent component = 0), clamped to the item's capacity. */
	public static long get(ItemStack stack) {
		Long value = stack.get(ModDataComponents.POUCH_ENERGY.get());
		if (value == null) {
			return 0L;
		}
		return Math.max(0L, Math.min(value, capacity(stack)));
	}

	/** Store {@code eu} clamped to {@code [0, capacity]}; 0 removes the component. */
	public static void set(ItemStack stack, long eu) {
		long clamped = Math.max(0L, Math.min(eu, capacity(stack)));
		if (clamped == 0L) {
			stack.remove(ModDataComponents.POUCH_ENERGY.get());
		} else {
			stack.set(ModDataComponents.POUCH_ENERGY.get(), clamped);
		}
		if (stack.getItem() instanceof EnergyPackItem) {
			// The pack looks different when dead (red light, pale cells), and the worn model is chosen by
			// its EQUIPPABLE asset — so the visual follows the charge from the one place charge changes.
			EnergyPackItem.refreshWornAsset(stack, clamped);
		}
		if (stack.getItem() instanceof JetpackItem) {
			// Same contract as the pack: the worn model follows the charge from the single write point.
			JetpackItem.refreshWornAsset(stack, clamped);
		}
	}

	/** Adjust stored EU by {@code delta} (may be negative); result is clamped. */
	public static void add(ItemStack stack, long delta) {
		set(stack, get(stack) + delta);
	}

	/**
	 * Spend {@code eu} from the item on behalf of {@code owner} — the one place a powered item's charge
	 * is ever debited (MOD-081). Nothing is written when the owner plays with infinite materials: EU is
	 * treated as tool wear, and creative does not wear tools down.
	 *
	 * <p>The guard lives here rather than at each call site, mirroring vanilla, where
	 * {@code ItemStack.processDurabilityChange} drops the damage inside {@code hurtAndBreak} and every
	 * caller stays naive. It also mirrors what every reference tech mod does (Mekanism guards
	 * {@code useEnergy} behind {@code isPlayingMode}, Thermal turns creative into a simulated extract,
	 * AE2 returns "power taken" without taking any).
	 *
	 * <p>Note this is a spend guard only, never an availability gate: {@link #get} still reports the
	 * real charge, so a flat item stays flat in creative instead of silently working forever. What the
	 * creative player keeps is the charge they already had.
	 */
	public static void spend(ItemStack stack, long eu, @Nullable Entity owner) {
		if (eu <= 0 || free(owner)) {
			return;
		}
		add(stack, -eu);
	}

	/**
	 * Whether {@code owner} gets their EU for free — a creative player, or a spectator, who has no
	 * business burning charge off the items they drift through the world with.
	 *
	 * <p>Creative is read as {@code hasInfiniteMaterials()} — the very ability vanilla's durability
	 * check reads — and not as {@code isCreative()}. For a real player the two agree by construction
	 * ({@code GameType.updatePlayerAbilities} sets {@code instabuild} for and only for CREATIVE), but
	 * the ability is the thing this rule is actually about, and it is the half a gametest can control:
	 * {@code makeMockServerPlayerInLevel} hardcodes {@code gameMode()} to CREATIVE with an override that
	 * {@code setGameMode} cannot undo, so a game-mode check would call every mock creative and quietly
	 * disable the drill's EU assertions. Spectator needs its own check regardless — vanilla clears
	 * {@code instabuild} for it.
	 */
	public static boolean free(@Nullable Entity owner) {
		return owner instanceof Player player && (player.hasInfiniteMaterials() || player.isSpectator());
	}

	/** Free space in the buffer: {@code capacity - stored}. */
	public static long room(ItemStack stack) {
		return capacity(stack) - get(stack);
	}
}

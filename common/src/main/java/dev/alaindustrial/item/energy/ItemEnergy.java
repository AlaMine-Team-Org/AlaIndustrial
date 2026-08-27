package dev.alaindustrial.item.energy;

import dev.alaindustrial.item.tool.ElectricChainsawItem;
import dev.alaindustrial.item.tool.ElectricDrillItem;
import dev.alaindustrial.item.tool.ElectricHoeItem;
import dev.alaindustrial.item.tool.ElectricSaberItem;
import dev.alaindustrial.item.tool.ElectricShovelItem;
import dev.alaindustrial.item.tool.MagnetItem;
import dev.alaindustrial.item.wearable.EnergyPackItem;
import dev.alaindustrial.item.wearable.FluxweaveArmorItem;
import dev.alaindustrial.item.wearable.JetpackItem;

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

	/**
	 * Max EU the item can hold, <b>per item</b>; 0 for items without an energy buffer.
	 *
	 * <p>"Per item" only matters for the Battery (MOD-083), the one powered item that stacks — everything
	 * else is {@code stacksTo(1)}, where per-item and per-stack are the same number. Callers that move
	 * energy into or out of a whole stack must go through {@link #stackAdd} and friends.
	 */
	public static long capacity(ItemStack stack) {
		if (stack.getItem() instanceof CrystalBlankItem blank) {
			return blank.tier().capacity();
		}
		if (stack.getItem() instanceof PouchItem) {
			return Config.lvPouchBuffer;
		}
		if (stack.getItem() instanceof BatteryItem) {
			return Config.batteryBuffer;
		}
		if (stack.getItem() instanceof EnergyPackItem) {
			return Config.energyPackBuffer;
		}
		if (stack.getItem() instanceof ElectricDrillItem) {
			return Config.electricDrillBuffer;
		}
		if (stack.getItem() instanceof ElectricChainsawItem) {
			return Config.electricChainsawBuffer;
		}
		if (stack.getItem() instanceof ElectricShovelItem) {
			return Config.electricShovelBuffer;
		}
		if (stack.getItem() instanceof ElectricHoeItem) {
			return Config.electricHoeBuffer;
		}
		if (stack.getItem() instanceof ElectricSaberItem) {
			return Config.electricSaberBuffer;
		}
		if (stack.getItem() instanceof MagnetItem) {
			return Config.magnetBuffer;
		}
		if (stack.getItem() instanceof JetpackItem) {
			return Config.jetpackBuffer;
		}
		// One branch for all four armour pieces (MOD-127): they share a buffer, and the class carries
		// its ArmorType, so four classes would only mean four copies of this and of the hook below.
		if (stack.getItem() instanceof FluxweaveArmorItem) {
			return Config.fluxweaveBuffer;
		}
		return 0L;
	}

	/**
	 * Max EU/tick this item accepts while sitting in a charge slot. A charger caps its transfer at
	 * {@code min(its own tier ceiling, inputRate(stack))} — the item's own ceiling, so a small pouch
	 * cannot be force-fed at a big charger's rate. 0 for items without a buffer.
	 */
	public static long inputRate(ItemStack stack) {
		if (stack.getItem() instanceof CrystalBlankItem blank) {
			return blank.tier().inputRate();
		}
		if (stack.getItem() instanceof PouchItem) {
			return EnergyTier.LV.maxVoltage();
		}
		if (stack.getItem() instanceof BatteryItem) {
			return Config.batteryInputRate;
		}
		if (stack.getItem() instanceof EnergyPackItem) {
			return Config.energyPackInputRate;
		}
		if (stack.getItem() instanceof ElectricDrillItem) {
			return Config.electricDrillInputRate;
		}
		if (stack.getItem() instanceof ElectricChainsawItem) {
			return Config.electricChainsawInputRate;
		}
		if (stack.getItem() instanceof ElectricShovelItem) {
			return Config.electricShovelInputRate;
		}
		if (stack.getItem() instanceof ElectricHoeItem) {
			return Config.electricHoeInputRate;
		}
		if (stack.getItem() instanceof ElectricSaberItem) {
			return Config.electricSaberInputRate;
		}
		if (stack.getItem() instanceof MagnetItem) {
			return Config.magnetInputRate;
		}
		if (stack.getItem() instanceof JetpackItem) {
			return Config.jetpackInputRate;
		}
		if (stack.getItem() instanceof FluxweaveArmorItem) {
			return Config.fluxweaveInputRate;
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
		if (stack.getItem() instanceof FluxweaveArmorItem) {
			// The armour swaps BOTH its worn asset and its attribute modifiers with the charge, so the
			// active bonuses can never disagree with the number in the tooltip.
			FluxweaveArmorItem.refreshWorn(stack, clamped);
		}
		if (stack.getItem() instanceof ElectricSaberItem) {
			// Same contract as the armour: damage, attack speed and reach follow the charge from the one
			// place charge changes, so the tooltip can never promise a hit the weapon cannot land.
			ElectricSaberItem.refreshAttributes(stack, clamped);
		}
	}

	/**
	 * Adjust stored EU by {@code delta} (may be negative); result is clamped.
	 *
	 * <p>A negative delta is refused for a crystal blank (MOD-504) — a blank only ever fills up. The
	 * rule lives here rather
	 * than in each discharge site because every one of them — the Battery Box slot, the CESU slot,
	 * {@link #spend} and so the Energy Pack distributor and every tool — ends up in this method. One
	 * guard covers them all, and a future caller inherits it without knowing about it.
	 */
	public static void add(ItemStack stack, long delta) {
		if (delta < 0 && stack.getItem() instanceof CrystalBlankItem) {
			return;
		}
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
	 * the in-level mock hardcodes {@code gameMode()} to CREATIVE with an override that
	 * {@code setGameMode} cannot undo, so a game-mode check would call every mock creative and quietly
	 * disable the drill's EU assertions. Spectator needs its own check regardless — vanilla clears
	 * {@code instabuild} for it.
	 */
	public static boolean free(@Nullable Entity owner) {
		return owner instanceof Player player && (player.hasInfiniteMaterials() || player.isSpectator());
	}

	/** Free space in the buffer, per item: {@code capacity - stored}. */
	public static long room(ItemStack stack) {
		return capacity(stack) - get(stack);
	}

	// ── Whole-stack arithmetic (MOD-083) ──────────────────────────────────────────────────────────
	//
	// Everything above is per item. The Battery is the one powered item that stacks, and its charge is
	// stored per item, so a charger facing a stack of 16 has to pay sixteen times — and, crucially, may
	// only move amounts that divide evenly by the count. Anything else would round energy into or out of
	// existence on every transfer, which over a few thousand ticks is a dupe or a leak.
	//
	// For a stacksTo(1) item every function here is the identity of its per-item twin, so call sites can
	// use the stack-aware form unconditionally.

	/** EU held by the whole stack: per-item charge × count. */
	public static long stackGet(ItemStack stack) {
		return get(stack) * stack.getCount();
	}

	/** Free space in the whole stack: per-item room × count. */
	public static long stackRoom(ItemStack stack) {
		return room(stack) * stack.getCount();
	}

	/**
	 * Move up to {@code eu} into (positive) or out of (negative) the <b>whole stack</b> and return what
	 * actually moved, signed. The result is always a multiple of {@code count}, and never exceeds the
	 * requested budget in magnitude: the per-item share is computed by integer division, which truncates
	 * toward zero, so a budget that does not divide evenly moves slightly less rather than slightly more.
	 *
	 * <p>Consequence worth knowing: a budget smaller than {@code count} moves nothing at all. That is why
	 * the battery stacks to 16 and not 64 — the LV ceiling is 32 EU/t, so a full stack still gets 2 EU per
	 * item per tick, while at 64 the share would round to zero and the stack would never charge.
	 */
	public static long stackAdd(ItemStack stack, long eu) {
		int count = stack.getCount();
		if (count <= 0 || eu == 0 || capacity(stack) <= 0) {
			return 0L;
		}
		// The unprimed-crystal guard has to be here too, not only in add(): this method REPORTS how much
		// it moved, and a discharge slot banks that number. Falling through to a refusing add() would
		// return a non-zero "moved" for energy that never left the item — EU created out of nothing.
		if (eu < 0 && stack.getItem() instanceof CrystalBlankItem) {
			return 0L;
		}
		long perItem = eu / count;
		if (perItem == 0) {
			return 0L;
		}
		perItem = perItem > 0 ? Math.min(perItem, room(stack)) : Math.max(perItem, -get(stack));
		if (perItem == 0) {
			return 0L;
		}
		add(stack, perItem);
		return perItem * count;
	}
}

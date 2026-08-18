package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.LightningRodGeneratorBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * Loader-neutral scenarios for the lightning rod generator (MOD-386). Both lanes replay these.
 *
 * <p><b>Why every scenario drives {@code strikeNow} instead of the weather.</b> The rod's own roll is
 * one-in-900 per tick and gated on {@code isRainingAt}, which additionally needs sky access — and the
 * NeoForge lane registers its tests with {@code skyAccess = false}, so a weather-driven scenario
 * could not run on both loaders at all. The roll itself is a pure function and is asserted at L1
 * ({@code LightningRodOutputTest#thunderWinsOverRainWhenBothAreTrue} and friends); what needs a live
 * world is everything downstream of it, which is exactly what the seam exposes.
 */
public final class LightningRodScenarios {
	private LightningRodScenarios() {
	}

	private static final BlockPos ROD = new BlockPos(1, 2, 1);

	/** Place a rod with {@code tip} installed (or empty when {@code tip} is null) and a drained buffer. */
	private static LightningRodGeneratorBlockEntity placeRod(GameTestHelper helper, ItemStack tip) {
		LightningRodGeneratorBlockEntity rod = AlaGameTestHelper.place(helper, ROD,
				ModContent.LIGHTNING_ROD_GENERATOR.get(), LightningRodGeneratorBlockEntity.class);
		rod.setItem(LightningRodGeneratorBlockEntity.TIP_SLOT, tip);
		rod.getEnergyStorage().setAmountUntracked(0L);
		return rod;
	}

	private static ItemStack copperTip() {
		return new ItemStack(ModContent.LIGHTNING_ROD_CONDUCTOR_TIP.get());
	}

	/** A strike into an empty capacitor banks the whole configured packet. */
	public static void strikeBanksIntoTheCapacitor(GameTestHelper helper) {
		LightningRodGeneratorBlockEntity rod = placeRod(helper, copperTip());
		if (rod.capacitorCharge() != 0) {
			helper.fail("a freshly placed rod should start with an empty capacitor, had "
					+ rod.capacitorCharge());
		}
		rod.strikeNow(helper.getLevel());
		if (rod.capacitorCharge() != Config.lightningRodStrikeEu) {
			helper.fail("an empty capacitor must bank the whole strike: expected "
					+ Config.lightningRodStrikeEu + " EU, banked " + rod.capacitorCharge());
		}
		helper.succeed();
	}

	/**
	 * With no tip installed the strike is lost outright — nothing is banked and, critically, no EU
	 * appears in the buffer either. This is the negative control for the whole feature: a rod that
	 * quietly produced EU with no consumable fitted would make the consumable pointless.
	 */
	public static void strikeWithoutTipBanksNothing(GameTestHelper helper) {
		LightningRodGeneratorBlockEntity rod = placeRod(helper, ItemStack.EMPTY);
		rod.strikeNow(helper.getLevel());
		if (rod.capacitorCharge() != 0) {
			helper.fail("a rod with no conductor tip banked " + rod.capacitorCharge() + " EU");
		}
		AlaGameTestHelper.drive(rod, helper, 40);
		if (rod.getEnergyStorage().getAmount() != 0L) {
			helper.fail("a rod with no conductor tip produced " + rod.getEnergyStorage().getAmount()
					+ " EU out of nothing");
		}
		helper.succeed();
	}

	/**
	 * A second strike on a full capacitor is wasted: the charge does not grow past the tip's capacity
	 * (no EU from thin air) and the tip pays the overload wear multiplier for it.
	 */
	public static void strikeOnFullCapacitorIsWastedAndWearsTheTip(GameTestHelper helper) {
		LightningRodGeneratorBlockEntity rod = placeRod(helper, copperTip());
		rod.strikeNow(helper.getLevel());
		int afterFirst = rod.capacitorCharge();
		int wearAfterFirst = rod.getItem(LightningRodGeneratorBlockEntity.TIP_SLOT).getDamageValue();
		if (afterFirst != rod.capacitorCapacity()) {
			helper.fail("this rig assumes one strike fills a copper tip exactly; banked " + afterFirst
					+ " of " + rod.capacitorCapacity() + " — the overload branch would never be reached");
		}

		rod.strikeNow(helper.getLevel());
		if (rod.capacitorCharge() > rod.capacitorCapacity()) {
			helper.fail("capacitor overflowed its capacity: " + rod.capacitorCharge()
					+ " > " + rod.capacitorCapacity());
		}
		if (rod.capacitorCharge() != afterFirst) {
			helper.fail("a wasted strike changed the stored charge from " + afterFirst + " to "
					+ rod.capacitorCharge());
		}
		int wearAfterSecond = rod.getItem(LightningRodGeneratorBlockEntity.TIP_SLOT).getDamageValue();
		if (wearAfterSecond <= wearAfterFirst) {
			helper.fail("a wasted strike must still wear the tip (overload penalty): damage stayed at "
					+ wearAfterSecond);
		}
		// The penalty is meant to be HARSHER than a caught strike, not merely non-zero.
		if (wearAfterSecond - wearAfterFirst <= wearAfterFirst) {
			helper.fail("overload wear (" + (wearAfterSecond - wearAfterFirst)
					+ ") should exceed a normal caught strike's wear (" + wearAfterFirst + ")");
		}
		// ...and NOT so harsh that one mistake destroys the cheapest tip outright. At the shipped
		// numbers (100 points, 20 per catch, ×2 on overload) a copper tip must still be installed
		// after one catch + one overload. This is the assertion that caught the original ×4 factor.
		if (rod.getItem(LightningRodGeneratorBlockEntity.TIP_SLOT).isEmpty()) {
			helper.fail("one caught strike plus one overload destroyed a copper tip outright — the "
					+ "overload wear factor is disproportionate to the tip's durability");
		}
		helper.succeed();
	}

	/** The banked burst leaves the capacitor as a flat per-tick rate and lands in the buffer. */
	public static void capacitorBleedsIntoTheBuffer(GameTestHelper helper) {
		LightningRodGeneratorBlockEntity rod = placeRod(helper, copperTip());
		rod.strikeNow(helper.getLevel());
		int banked = rod.capacitorCharge();

		AlaGameTestHelper.drive(rod, helper, 20);
		long buffered = rod.getEnergyStorage().getAmount();
		if (buffered <= 0L) {
			helper.fail("20 ticks after a strike the buffer is still empty — the capacitor is not bleeding");
		}
		if (rod.capacitorCharge() >= banked) {
			helper.fail("the capacitor did not drain: still holding " + rod.capacitorCharge()
					+ " of " + banked);
		}
		// Rate ceiling: the bleed must respect the LV cap rather than dumping the burst in one tick.
		int drained = banked - rod.capacitorCharge();
		int ceiling = Config.lightningRodMaxBleedEuPerTick * 20;
		if (drained > ceiling) {
			helper.fail("bleed exceeded the LV ceiling: drained " + drained + " EU in 20 ticks, cap is "
					+ ceiling);
		}
		helper.succeed();
	}

	/**
	 * The capacitor is a store, not a leak: with the buffer already full the reserve must stay put
	 * instead of draining into nowhere. Without this the player's banked strike would evaporate
	 * whenever their network was saturated.
	 */
	public static void aFullBufferDoesNotDrainTheCapacitor(GameTestHelper helper) {
		LightningRodGeneratorBlockEntity rod = placeRod(helper, copperTip());
		rod.strikeNow(helper.getLevel());
		int banked = rod.capacitorCharge();
		rod.getEnergyStorage().setAmountUntracked(rod.getEnergyStorage().getCapacity());

		AlaGameTestHelper.drive(rod, helper, 40);
		if (rod.capacitorCharge() != banked) {
			helper.fail("a full buffer drained the capacitor from " + banked + " to "
					+ rod.capacitorCharge() + " — banked EU was destroyed");
		}
		helper.succeed();
	}

	/** Charge and wear survive a save/load round trip, so a relog never resets a banked strike. */
	public static void capacitorSurvivesSaveAndLoad(GameTestHelper helper) {
		LightningRodGeneratorBlockEntity rod = placeRod(helper, copperTip());
		rod.strikeNow(helper.getLevel());
		int banked = rod.capacitorCharge();

		var level = helper.getLevel();
		var registries = level.registryAccess();
		BlockPos abs = helper.absolutePos(ROD);
		CompoundTag tag = rod.saveCustomOnly(registries);
		LightningRodGeneratorBlockEntity reloaded =
				new LightningRodGeneratorBlockEntity(abs, level.getBlockState(abs));
		reloaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));

		if (reloaded.capacitorCharge() != banked) {
			helper.fail("capacitor charge did not survive save/load: " + banked + " -> "
					+ reloaded.capacitorCharge());
		}
		helper.succeed();
	}

	/**
	 * When a tip finally wears out mid-strike, the charge it was holding goes with it. The capacitor
	 * is the tip, so a rod left holding EU in a capacitor that no longer exists is an inconsistent
	 * state: {@link LightningRodGeneratorBlockEntity#bleed} refuses to drain without a tip, and the
	 * gauge would read past full. Driven by hand-damaging the tip to one point from death rather than
	 * by looping strikes, so the scenario stays independent of the durability balance.
	 */
	public static void aBreakingTipTakesItsStoredChargeWithIt(GameTestHelper helper) {
		LightningRodGeneratorBlockEntity rod = placeRod(helper, copperTip());
		rod.strikeNow(helper.getLevel());
		if (rod.capacitorCharge() <= 0) {
			helper.fail("rig precondition: the first strike banked nothing");
		}
		ItemStack tip = rod.getItem(LightningRodGeneratorBlockEntity.TIP_SLOT);
		tip.setDamageValue(tip.getMaxDamage() - 1);

		rod.strikeNow(helper.getLevel());
		if (!rod.getItem(LightningRodGeneratorBlockEntity.TIP_SLOT).isEmpty()) {
			helper.fail("rig precondition: the tip was meant to break on this strike");
		}
		if (rod.capacitorCharge() != 0) {
			helper.fail("a broken tip left " + rod.capacitorCharge()
					+ " EU stranded in a capacitor that no longer exists");
		}
		helper.succeed();
	}
}

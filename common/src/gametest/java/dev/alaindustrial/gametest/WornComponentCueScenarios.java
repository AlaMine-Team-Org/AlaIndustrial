package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.LightningRodGeneratorBlockEntity;
import dev.alaindustrial.block.entity.WaterMillBlockEntity;
import dev.alaindustrial.block.entity.WindMillBlockEntity;
import dev.alaindustrial.core.environment.GeneratorConfig;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Loader-neutral world gametests for the worn rotor / wheel warning (MOD-654): a running wind or water mill whose
 * part is nearly spent creaks and puffs smoke; above the threshold, idle, repaired or on the lightning rod it
 * does not. The server-side effects are observed through {@code wornCuesSent()}, the counter of the cue
 * component — a gametest cannot hear a sound or see a particle. Each scenario also checks that the mill IS
 * producing, so "no cue" cannot pass for the wrong reason.
 *
 * <p>The pauses are shortened to a few ticks with {@link ConfigOverrides} (the defaults are tens of seconds);
 * the threshold itself is the production default — 10 % of the max, capped at 120 points.
 */
public final class WornComponentCueScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(WornComponentCueScenarios::windMill_wornRotorCues, "wind_mill_worn_rotor_cues")
						.ticks(120).sky(),
				RosterEntry.of(WornComponentCueScenarios::windMill_healthyRotorIsSilent,
								"wind_mill_healthy_rotor_is_silent")
						.ticks(120).sky(),
				RosterEntry.of(WornComponentCueScenarios::windMill_idleMillIsSilent, "wind_mill_idle_worn_rotor_silent")
						.ticks(120).sky(),
				RosterEntry.of(WornComponentCueScenarios::windMill_repairedRotorFallsSilent,
								"wind_mill_repaired_rotor_falls_silent")
						.ticks(120).sky(),
				RosterEntry.of(WornComponentCueScenarios::windMill_disabledKnobIsSilent,
								"wind_mill_cue_disabled_by_knob")
						.ticks(120).sky(),
				RosterEntry.of(WornComponentCueScenarios::waterMill_wornWheelCues, "water_mill_worn_wheel_cues")
						.ticks(20, 100),
				RosterEntry.of(WornComponentCueScenarios::lightningRod_wornTipStaysSilent,
								"lightning_rod_worn_tip_stays_silent")
						.ticks(20, 60));

		private Roster() {}
	}

	private WornComponentCueScenarios() {}

	private static final BlockPos POS = new BlockPos(1, 2, 1);
	/** The rate tests need a non-zero height base; see {@code WindMillScenarios} for why the mill stands this high. */
	private static final BlockPos RAISED_POS = new BlockPos(1, 20, 1);
	/** Ticks that cover one rate sample plus several shortened pauses. */
	private static final int RUN_TICKS = GeneratorConfig.windMillSampleTicks + 80;

	private static WindMillBlockEntity placeRaisedWind(GameTestHelper helper, int damageLeft) {
		for (int y = POS.getY(); y < RAISED_POS.getY(); y++) {
			helper.setBlock(new BlockPos(RAISED_POS.getX(), y, RAISED_POS.getZ()), Blocks.GLASS);
		}
		helper.setBlock(RAISED_POS, ModContent.WIND_MILL.get());
		WindMillBlockEntity mill = helper.getBlockEntity(RAISED_POS, WindMillBlockEntity.class);
		if (mill == null) {
			helper.fail("raised wind mill block entity missing after placement");
		}
		mill.setItem(WindMillBlockEntity.ROTOR_SLOT, rotorWithLeft(damageLeft));
		var level = helper.getLevel();
		level.getWeatherData().setRaining(false);
		level.getWeatherData().setThundering(false);
		level.setRainLevel(0.0f);
		return mill;
	}

	/** A rotor with {@code left} durability points remaining ({@code -1} = brand new). */
	private static ItemStack rotorWithLeft(int left) {
		ItemStack rotor = new ItemStack(ModContent.WINDMILL_ROTOR.get());
		if (left >= 0) {
			rotor.setDamageValue(rotor.getMaxDamage() - left);
		}
		return rotor;
	}

	private static void shortPauses(ConfigOverrides o) {
		o.set("wornComponentCueSlowTicks", 8);
		o.set("wornComponentCueFastTicks", 8);
	}

	private static void requireProducing(GameTestHelper helper, WindMillBlockEntity mill) {
		if (mill.getDataAccess().get(2) <= 0) {
			helper.fail("the rig must produce for this scenario to mean anything; rate="
					+ mill.getDataAccess().get(2));
		}
	}

	/** 50 points left of 1000 (inside the 100-point window): a producing mill creaks and smokes. */
	public static void windMill_wornRotorCues(GameTestHelper helper) {
		try (ConfigOverrides o = ConfigOverrides.sync()) {
			shortPauses(o);
			WindMillBlockEntity mill = placeRaisedWind(helper, 50);
			AlaGameTestHelper.drive(mill, helper, RUN_TICKS);
			requireProducing(helper, mill);
			if (mill.wornCuesSent() < 2) {
				helper.fail("a nearly spent rotor on a running mill should have creaked repeatedly, cues sent: "
						+ mill.wornCuesSent());
			}
			if (mill.getItem(WindMillBlockEntity.ROTOR_SLOT).isEmpty()) {
				helper.fail("the rig broke the rotor — the scenario would not be about the warning");
			}
			helper.succeed();
		}
	}

	/** A brand-new rotor and one with 200 points left (outside the window) stay silent while producing. */
	public static void windMill_healthyRotorIsSilent(GameTestHelper helper) {
		try (ConfigOverrides o = ConfigOverrides.sync()) {
			shortPauses(o);
			WindMillBlockEntity mill = placeRaisedWind(helper, -1);
			AlaGameTestHelper.drive(mill, helper, RUN_TICKS);
			requireProducing(helper, mill);
			if (mill.wornCuesSent() != 0) {
				helper.fail("a brand-new rotor creaked: " + mill.wornCuesSent());
			}
			mill.setItem(WindMillBlockEntity.ROTOR_SLOT, rotorWithLeft(200));
			AlaGameTestHelper.drive(mill, helper, 40);
			if (mill.wornCuesSent() != 0) {
				helper.fail("a rotor with 200 of 1000 points left (outside the 100-point window) creaked: "
						+ mill.wornCuesSent());
			}
			helper.succeed();
		}
	}

	/** A worn rotor in a mill that produces nothing (no wind range) does not creak. */
	public static void windMill_idleMillIsSilent(GameTestHelper helper) {
		try (ConfigOverrides o = ConfigOverrides.sync()) {
			shortPauses(o);
			o.set("windMillMaxBaseEuPerTick", 0); // rate 0 at any height
			WindMillBlockEntity mill = placeRaisedWind(helper, 50);
			AlaGameTestHelper.drive(mill, helper, RUN_TICKS);
			if (mill.getDataAccess().get(2) != 0) {
				helper.fail("the idle rig produced " + mill.getDataAccess().get(2));
			}
			if (mill.wornCuesSent() != 0) {
				helper.fail("an idle mill creaked: " + mill.wornCuesSent());
			}
			helper.succeed();
		}
	}

	/** After a repair (damage 0) the warning stops; the count does not move again. */
	public static void windMill_repairedRotorFallsSilent(GameTestHelper helper) {
		try (ConfigOverrides o = ConfigOverrides.sync()) {
			shortPauses(o);
			WindMillBlockEntity mill = placeRaisedWind(helper, 50);
			AlaGameTestHelper.drive(mill, helper, RUN_TICKS);
			requireProducing(helper, mill);
			int before = mill.wornCuesSent();
			if (before == 0) {
				helper.fail("the worn rotor never creaked, so the repair check would prove nothing");
			}
			mill.setItem(WindMillBlockEntity.ROTOR_SLOT, rotorWithLeft(-1)); // repaired: pristine
			AlaGameTestHelper.drive(mill, helper, 60);
			if (mill.wornCuesSent() != before) {
				helper.fail("a repaired rotor kept creaking: " + before + " -> " + mill.wornCuesSent());
			}
			helper.succeed();
		}
	}

	/** The master switch: {@code wornComponentCueEnabled = false} silences even a nearly broken rotor. */
	public static void windMill_disabledKnobIsSilent(GameTestHelper helper) {
		try (ConfigOverrides o = ConfigOverrides.sync()) {
			shortPauses(o);
			o.set("wornComponentCueEnabled", false);
			WindMillBlockEntity mill = placeRaisedWind(helper, 5);
			AlaGameTestHelper.drive(mill, helper, RUN_TICKS);
			requireProducing(helper, mill);
			if (mill.wornCuesSent() != 0) {
				helper.fail("the disabled warning still fired: " + mill.wornCuesSent());
			}
			helper.succeed();
		}
	}

	/** A water mill with a worn wheel creaks while the water drives it; a fresh wheel does not. */
	public static void waterMill_wornWheelCues(GameTestHelper helper) {
		try (ConfigOverrides o = ConfigOverrides.sync()) {
			shortPauses(o);
			WaterMillBlockEntity mill = AlaGameTestHelper.place(helper, POS, ModContent.WATER_MILL.get(),
					WaterMillBlockEntity.class);
			BlockState flowing = Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 1);
			helper.setBlock(POS.relative(Direction.NORTH).below(), flowing); // the undershot cell: one driven side
			mill.setItem(WaterMillBlockEntity.WHEEL_SLOT, new ItemStack(ModContent.WATER_MILL_WHEEL.get()));
			AlaGameTestHelper.drive(mill, helper, 30);
			if (mill.getDataAccess().get(2) <= 0) {
				helper.fail("the water rig must drive the wheel for this scenario to mean anything");
			}
			if (mill.wornCuesSent() != 0) {
				helper.fail("a brand-new wheel creaked: " + mill.wornCuesSent());
			}
			ItemStack installed = mill.getItem(WaterMillBlockEntity.WHEEL_SLOT);
			installed.setDamageValue(installed.getMaxDamage() - 50);
			AlaGameTestHelper.drive(mill, helper, 40);
			if (mill.wornCuesSent() < 2) {
				helper.fail("a nearly spent wheel on a running water mill should have cued repeatedly: "
						+ mill.wornCuesSent());
			}
			helper.succeed();
		}
	}

	/** The lightning rod also calls {@code wearComponent} for its tip; it must stay silent (no turning part). */
	public static void lightningRod_wornTipStaysSilent(GameTestHelper helper) {
		try (ConfigOverrides o = ConfigOverrides.sync()) {
			shortPauses(o);
			LightningRodGeneratorBlockEntity rod = AlaGameTestHelper.place(helper, POS,
					ModContent.LIGHTNING_ROD_GENERATOR.get(), LightningRodGeneratorBlockEntity.class);
			ItemStack tip = new ItemStack(ModContent.LIGHTNING_ROD_CONDUCTOR_TIP.get());
			int seeded = tip.getMaxDamage() - 8; // 8 left of 100: deep inside any warning window
			tip.setDamageValue(seeded);
			rod.setItem(LightningRodGeneratorBlockEntity.TIP_SLOT, tip);
			rod.strikeNow(helper.getLevel());
			AlaGameTestHelper.drive(rod, helper, 30);
			if (rod.wornCuesSent() != 0) {
				helper.fail("the lightning rod creaked over a worn tip: " + rod.wornCuesSent());
			}
			ItemStack after = rod.getItem(LightningRodGeneratorBlockEntity.TIP_SLOT);
			if (!after.isEmpty() && after.getDamageValue() <= seeded) {
				helper.fail("the tip did not wear, so the strike never reached wearComponent");
			}
			helper.succeed();
		}
	}
}

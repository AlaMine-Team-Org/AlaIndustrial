package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.block.entity.WaterMillBlockEntity;
import dev.alaindustrial.block.entity.WindMillBlockEntity;
import dev.alaindustrial.core.machine.ComponentTier;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * L2 coverage for the MOD-385 three-grade component ladder — rotor and wheel, in a real world, on a
 * real block entity.
 *
 * <p><b>Every output assertion here is comparative, never an oracle call.</b> The obvious way to test
 * "the reinforced rotor produces 25 % more" is to compute the expectation with
 * {@code WindMillOutput.euFor} — the very method under test — which proves only that the method equals
 * itself. {@code WindMillGameTest.expectedRate} does exactly that today, and the comment in
 * {@code GeneratorEnergyScenarios} spells out why it is the wrong shape. These scenarios instead run
 * the SAME mill, in the SAME world state, twice — once per grade — and compare the energy it actually
 * banked. A bug that breaks the multiplier wiring reddens them; a bug in {@code euFor} that both the
 * production path and the oracle share cannot hide.
 */
public final class ComponentTierScenarios {
	private ComponentTierScenarios() {}

	/**
	 * Raised mill position for the wind scenarios. The gametest world's sea level is −63 and the
	 * structure floor sits at absY ≈ −59, which the MOD-347 wind profile rounds to base 0 — a mill there
	 * produces nothing and every grade comparison would be vacuously equal. A glass pillar to relative
	 * Y = 20 clears that, exactly as {@code WindMillGameTest.placeRaised} does (glass is transparent to
	 * skylight, and the mill's clearance cone is in FRONT, not below, so the pillar does not stall it).
	 */
	private static final BlockPos WIND_POS = new BlockPos(1, 20, 1);
	private static final BlockPos WATER_POS = new BlockPos(1, 2, 1);
	/** Long enough for a sample window to elapse and for several EU to accumulate. */
	private static final int DRIVE_TICKS = 120;

	/** Flowing (non-source) water — the only kind that drives a wheel since MOD-188. */
	private static BlockState flowingWater() {
		return Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 1);
	}

	/**
	 * Bank EU for {@link #DRIVE_TICKS} with {@code component} installed in {@code slot}, starting from
	 * an empty buffer, and return what the machine actually stored. The buffer is drained first so the
	 * measurement is of production, not of a leftover charge.
	 */
	private static long bankedWith(GameTestHelper helper, MachineBlockEntity machine, int slot,
			Item component) {
		machine.setItem(slot, new ItemStack(component));
		machine.getEnergyStorage().setAmountUntracked(0L);
		AlaGameTestHelper.drive(machine, helper, DRIVE_TICKS);
		return machine.getEnergyStorage().getAmount();
	}

	/**
	 * A wind mill high enough above sea level to make real EU, under open sky. Placed at the test
	 * region's own height, so the absolute Y decides the rate — the comparison is unaffected either way
	 * because every grade is measured at the very same spot.
	 */
	private static WindMillBlockEntity placeWindMill(GameTestHelper helper) {
		for (int y = 2; y < WIND_POS.getY(); y++) {
			helper.setBlock(new BlockPos(WIND_POS.getX(), y, WIND_POS.getZ()), Blocks.GLASS);
		}
		helper.setBlock(WIND_POS, ModContent.WIND_MILL.get());
		WindMillBlockEntity mill = helper.getBlockEntity(WIND_POS, WindMillBlockEntity.class);
		if (mill == null) {
			helper.fail("raised wind mill block entity missing after placement");
		}
		return mill;
	}

	/**
	 * Run {@code body} with the wind mill's height base scaled up, then restore the shipped values.
	 *
	 * <p><b>Why the rig needs this.</b> Even on its pillar the arena mill only reaches base 1 EU/t, and
	 * EU/t is an integer: {@code round(1 × 1.25) == 1}, so the reinforced rotor would measure identical
	 * to the wooden one and the comparison would prove nothing. Raising {@code maxBase} moves the rig
	 * into the range where the ×1.25 and ×1.5 steps land on distinct integers — the same range a real
	 * mill reaches by being built near the cloud deck, which no gametest arena can offer. The
	 * multiplier logic under test is untouched by this; only the rig's operating point moves.
	 *
	 * @param cap value for {@code windMillMaxEuPerTick} — pass the shipped one to exercise the cap,
	 *     or a high one to keep the cap out of the way of an output comparison
	 */
	private static void withBoostedWind(int cap, Runnable body) {
		int savedBase = Config.windMillMaxBaseEuPerTick;
		int savedCap = Config.windMillMaxEuPerTick;
		try {
			Config.windMillMaxBaseEuPerTick = 40;
			Config.windMillMaxEuPerTick = cap;
			body.run();
		} finally {
			Config.windMillMaxBaseEuPerTick = savedBase;
			Config.windMillMaxEuPerTick = savedCap;
		}
	}

	private static WaterMillBlockEntity placeWaterMill(GameTestHelper helper) {
		WaterMillBlockEntity mill = AlaGameTestHelper.place(helper, WATER_POS,
				ModContent.WATER_MILL.get(), WaterMillBlockEntity.class);
		// Drive all four wheel-swept cells (MOD-352) so the mill runs at its structural maximum, where
		// the grade difference is largest and integer rounding cannot swallow it.
		BlockPos front = WATER_POS.relative(Direction.NORTH);
		helper.setBlock(front.above(), flowingWater());
		helper.setBlock(front.below(), flowingWater());
		helper.setBlock(front.relative(Direction.NORTH.getClockWise()), flowingWater());
		helper.setBlock(front.relative(Direction.NORTH.getCounterClockWise()), flowingWater());
		return mill;
	}

	/**
	 * A better rotor makes strictly more EU in identical conditions — same mill, same block, same
	 * weather, same tick budget, only the item in the slot differs.
	 */
	public static void windMill_betterRotorProducesMoreEu(GameTestHelper helper) {
		WindMillBlockEntity mill = placeWindMill(helper);
		// Cap lifted well clear so this measures the MULTIPLIER, not the clamp; the clamp has its own
		// scenario below, which deliberately runs at the shipped cap.
		withBoostedWind(4096, () -> {
			long wooden = bankedWith(helper, mill, WindMillBlockEntity.ROTOR_SLOT,
					ModContent.WINDMILL_ROTOR.get());
			if (wooden <= 0) {
				helper.fail("wooden rotor banked no EU at all — the rig produces nothing, so a grade "
						+ "comparison would be vacuous (check height/sky at this test position)");
				return;
			}
			long reinforced = bankedWith(helper, mill, WindMillBlockEntity.ROTOR_SLOT,
					ModContent.WINDMILL_ROTOR_REINFORCED.get());
			long advanced = bankedWith(helper, mill, WindMillBlockEntity.ROTOR_SLOT,
					ModContent.WINDMILL_ROTOR_ADVANCED.get());
			if (reinforced <= wooden) {
				helper.fail("reinforced rotor banked " + reinforced + " EU vs the wooden rotor's " + wooden
						+ " — the grade multiplier is not reaching production");
				return;
			}
			if (advanced <= reinforced) {
				helper.fail("advanced rotor banked " + advanced + " EU vs the reinforced rotor's "
						+ reinforced + " — the top grade must beat the middle one");
				return;
			}
			helper.succeed();
		});
	}

	/** The same comparison for the water mill, at its full four-cell drive. */
	public static void waterMill_betterWheelProducesMoreEu(GameTestHelper helper) {
		WaterMillBlockEntity mill = placeWaterMill(helper);
		long wooden = bankedWith(helper, mill, WaterMillBlockEntity.WHEEL_SLOT,
				ModContent.WATER_MILL_WHEEL.get());
		if (wooden <= 0) {
			helper.fail("wooden wheel banked no EU — the four drive cells are not carrying a current, "
					+ "so this comparison would be vacuous");
			return;
		}
		long reinforced = bankedWith(helper, mill, WaterMillBlockEntity.WHEEL_SLOT,
				ModContent.WATER_MILL_WHEEL_REINFORCED.get());
		long advanced = bankedWith(helper, mill, WaterMillBlockEntity.WHEEL_SLOT,
				ModContent.WATER_MILL_WHEEL_ADVANCED.get());
		if (reinforced <= wooden) {
			helper.fail("reinforced wheel banked " + reinforced + " EU vs the wooden wheel's " + wooden);
			return;
		}
		if (advanced <= reinforced) {
			helper.fail("advanced wheel banked " + advanced + " EU vs the reinforced wheel's " + reinforced);
			return;
		}
		helper.succeed();
	}

	/**
	 * The negative control demanded by the task: the plain wooden component must behave EXACTLY as it
	 * did before MOD-385. Ratcheting the T1 multiplier above 1.0 — the mistake this guards — would make
	 * every shipped balance number in {@code PERFORMANCE.md} wrong at once, silently.
	 */
	public static void baselineGradeIsUnchanged(GameTestHelper helper) {
		if (ComponentTier.WINDMILL_ROTOR.outputMultiplier() != 1.0f) {
			helper.fail("the wooden rotor's multiplier is "
					+ ComponentTier.WINDMILL_ROTOR.outputMultiplier() + ", not 1.0 — T1 output has moved");
			return;
		}
		if (ComponentTier.WATER_MILL_WHEEL.outputMultiplier() != 1.0f) {
			helper.fail("the wooden wheel's multiplier is "
					+ ComponentTier.WATER_MILL_WHEEL.outputMultiplier() + ", not 1.0");
			return;
		}
		WaterMillBlockEntity mill = placeWaterMill(helper);
		long banked = bankedWith(helper, mill, WaterMillBlockEntity.WHEEL_SLOT,
				ModContent.WATER_MILL_WHEEL.get());
		// Four driven cells at waterMillEuPerTick, times the global economy knob, for DRIVE_TICKS.
		long expected = (long) Math.max(1, Math.round(4 * Config.waterMillEuPerTick
				* Config.globalEuRateMultiplier)) * DRIVE_TICKS;
		long capped = Math.min(expected, mill.getEnergyStorage().getCapacity());
		if (banked != capped) {
			helper.fail("the wooden wheel banked " + banked + " EU over " + DRIVE_TICKS
					+ " ticks, but the pre-MOD-385 rate is " + capped + " — the baseline has drifted");
			return;
		}
		helper.succeed();
	}

	/**
	 * No grade may lift a generator's {@code *MaxEuPerTick}. Checked on the block entity's own synced
	 * mechanical rate rather than on {@code euFor}, so it covers the wiring as well as the arithmetic.
	 */
	public static void noGradeExceedsTheWindMillCap(GameTestHelper helper) {
		WindMillBlockEntity mill = placeWindMill(helper);
		// The clamp is exercised by lowering the CEILING onto the rig, not by trying to push the rig up
		// to the shipped ceiling. Two earlier attempts at the latter failed for rig reasons rather than
		// code reasons, and both are worth remembering:
		//   * raising maxBase also moves WindProfile's ridge, so the height factor drops and the two
		//     changes largely cancel — the arena mill stayed at ~5 EU/t against a cap of 8;
		//   * forcing a thunderstorm with setRaining/setThundering does not make Level.isThundering()
		//     true (it reads the rain/thunder LEVEL, not the flag), so the weather multiplier never
		//     applied either.
		// TEST_CAP = 4 sits below what this rig makes at the top grade (5 EU/t), so the advanced rotor
		// genuinely overshoots and must be clamped — which is exactly the property under test. That the
		// SHIPPED caps hold for any multiplier is covered exhaustively at L1
		// (ComponentMultiplierOutputTest.noMultiplierHoweverAbsurdCanBreachTheCap).
		final int testCap = 4;
		withBoostedWind(testCap, () -> {
			int highest = 0;
			for (Supplier<Item> grade : List.of(
					(Supplier<Item>) () -> ModContent.WINDMILL_ROTOR.get(),
					() -> ModContent.WINDMILL_ROTOR_REINFORCED.get(),
					() -> ModContent.WINDMILL_ROTOR_ADVANCED.get())) {
				mill.setItem(WindMillBlockEntity.ROTOR_SLOT, new ItemStack(grade.get()));
				mill.getEnergyStorage().setAmountUntracked(0L);
				AlaGameTestHelper.drive(mill, helper, DRIVE_TICKS);
				int mechanical = mill.getDataAccess().get(2);
				highest = Math.max(highest, mechanical);
				if (mechanical > testCap) {
					helper.fail(grade.get() + " drove the mill to " + mechanical + " EU/t, breaching the "
							+ "cap of " + testCap + " — the grade multiplier is escaping the clamp");
					return;
				}
				if (mechanical > 32) {
					helper.fail(grade.get() + " drove the mill to " + mechanical
							+ " EU/t, breaching the LV tier voltage of 32");
					return;
				}
			}
			// Without this the loop above would pass on a rig that never reached the ceiling at all,
			// asserting nothing — the exact false-green this scenario exists to avoid.
			if (highest < testCap) {
				helper.fail("no grade got the mill to its cap (peak " + highest + " of " + testCap
						+ " EU/t), so the clamp was never exercised — this rig proves nothing");
				return;
			}
			helper.succeed();
		});
	}

	/** Every rotor slot on every wind mill accepts all three grades and still refuses a foreign item. */
	public static void rotorSlotAcceptsEveryGrade(GameTestHelper helper) {
		WindMillBlockEntity mill = placeWindMill(helper);
		for (Item grade : List.of(ModContent.WINDMILL_ROTOR.get(),
				ModContent.WINDMILL_ROTOR_REINFORCED.get(),
				ModContent.WINDMILL_ROTOR_ADVANCED.get())) {
			if (!mill.canPlaceItem(WindMillBlockEntity.ROTOR_SLOT, new ItemStack(grade))) {
				helper.fail("the rotor slot rejected " + grade);
				return;
			}
		}
		if (mill.canPlaceItem(WindMillBlockEntity.ROTOR_SLOT, new ItemStack(Items.STICK))) {
			helper.fail("the rotor slot accepted a stick — widening it to a tag must not open it up");
			return;
		}
		// A wheel is a component of the OTHER family: the two tags must not bleed into each other.
		if (mill.canPlaceItem(WindMillBlockEntity.ROTOR_SLOT,
				new ItemStack(ModContent.WATER_MILL_WHEEL.get()))) {
			helper.fail("the rotor slot accepted a water wheel — the two component tags overlap");
			return;
		}
		helper.succeed();
	}

	/** The wheel slot's twin of {@link #rotorSlotAcceptsEveryGrade}. */
	public static void wheelSlotAcceptsEveryGrade(GameTestHelper helper) {
		WaterMillBlockEntity mill = placeWaterMill(helper);
		for (Item grade : List.of(ModContent.WATER_MILL_WHEEL.get(),
				ModContent.WATER_MILL_WHEEL_REINFORCED.get(),
				ModContent.WATER_MILL_WHEEL_ADVANCED.get())) {
			if (!mill.canPlaceItem(WaterMillBlockEntity.WHEEL_SLOT, new ItemStack(grade))) {
				helper.fail("the wheel slot rejected " + grade);
				return;
			}
		}
		if (mill.canPlaceItem(WaterMillBlockEntity.WHEEL_SLOT, new ItemStack(Items.STICK))) {
			helper.fail("the wheel slot accepted a stick");
			return;
		}
		if (mill.canPlaceItem(WaterMillBlockEntity.WHEEL_SLOT,
				new ItemStack(ModContent.WINDMILL_ROTOR.get()))) {
			helper.fail("the wheel slot accepted a wind mill rotor — the two component tags overlap");
			return;
		}
		// MOD-179 must survive the widening: automation may only insert into an EMPTY slot.
		mill.setItem(WaterMillBlockEntity.WHEEL_SLOT,
				new ItemStack(ModContent.WATER_MILL_WHEEL_ADVANCED.get()));
		if (mill.canPlaceItemThroughFace(WaterMillBlockEntity.WHEEL_SLOT,
				new ItemStack(ModContent.WATER_MILL_WHEEL.get()), Direction.UP)) {
			helper.fail("automation inserted a wheel into an occupied slot — the MOD-179 guard is gone");
			return;
		}
		helper.succeed();
	}

	/**
	 * An upgraded rotor must survive the T1→T2 evolution <b>with its accumulated wear</b>. MOD-211 is
	 * the precedent for losing a slot's contents on evolution, and this rotor is now an expensive item:
	 * dropping it, or silently resetting it to a pristine wooden one, would be a costly bug.
	 */
	public static void evolutionCarriesTheUpgradedRotor(GameTestHelper helper) {
		WindMillBlockEntity mill = placeWindMill(helper);
		ItemStack worn = new ItemStack(ModContent.WINDMILL_ROTOR_ADVANCED.get());
		int damage = worn.getMaxDamage() / 3;
		worn.setDamageValue(damage);
		mill.setItem(WindMillBlockEntity.ROTOR_SLOT, worn);
		mill.setItem(WindMillBlockEntity.CHIP_SLOT, new ItemStack(ModContent.ALIGNMENT_CHIP_DAY.get()));
		mill.setEvolveProgressTicks(Config.windMillEvolveTicks - 1);
		AlaGameTestHelper.drive(mill, helper, 5);

		if (helper.getBlockEntity(WIND_POS, MachineBlockEntity.class) instanceof MachineBlockEntity evolved) {
			ItemStack carried = evolved.getItem(0);
			if (carried.isEmpty()) {
				helper.fail("the advanced rotor vanished on evolution (the MOD-211 failure mode)");
				return;
			}
			if (!carried.is(ModContent.WINDMILL_ROTOR_ADVANCED.get())) {
				helper.fail("evolution replaced the advanced rotor with " + carried.getItem()
						+ " — the grade was not carried across");
				return;
			}
			if (carried.getDamageValue() != damage) {
				helper.fail("the carried rotor's wear is " + carried.getDamageValue() + ", expected "
						+ damage + " — evolution reset the durability bar");
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("the mill did not evolve, so the rotor carry-over was never exercised");
	}
}

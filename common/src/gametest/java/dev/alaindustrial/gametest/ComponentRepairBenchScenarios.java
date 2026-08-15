package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.ComponentRepairBenchBlockEntity;
import dev.alaindustrial.block.entity.WaterMillBlockEntity;
import dev.alaindustrial.block.entity.WindMillBlockEntity;
import dev.alaindustrial.core.machine.ComponentRepair;
import dev.alaindustrial.core.machine.ComponentTier;
import dev.alaindustrial.item.misc.DurableComponentItem;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * L2 coverage for the component repair bench (MOD-384), in a real world on a real block entity.
 *
 * <p><b>Every assertion reads the stack, never the formula.</b> The tempting shape —
 * "expected = ComponentRepair.maxDamageAfter(...)" — would compare the production path against the
 * very method it uses and pass no matter what the bench actually wrote (the tautology trap
 * {@code GeneratorEnergyScenarios} warns about, and that {@code WindMillGameTest.expectedRate} fell
 * into). The ceilings below are therefore literal numbers derived from the shipped config: a T1
 * component is 1000 durability and the step is 20 %, so the ladder is 800 / 600 / 400 / 200 and the
 * fifth repair is refused because it would leave nothing.
 */
public final class ComponentRepairBenchScenarios {
	private ComponentRepairBenchScenarios() {}

	private static final BlockPos BENCH = new BlockPos(1, 2, 1);
	/** Comfortably longer than a T1 repair (625 ticks at the shipped 8 EU/t). */
	private static final int T1_TICKS = 700;

	// --- rig -----------------------------------------------------------------------------------

	private static ComponentRepairBenchBlockEntity placeBench(GameTestHelper helper) {
		helper.setBlock(BENCH, ModContent.COMPONENT_REPAIR_BENCH.get());
		ComponentRepairBenchBlockEntity bench =
				helper.getBlockEntity(BENCH, ComponentRepairBenchBlockEntity.class);
		if (bench == null) {
			helper.fail("repair bench block entity missing after placement");
		}
		return bench;
	}

	/** A component of {@code item} worn to {@code damage}, with {@code repairs} already on the clock. */
	private static ItemStack worn(Item item, int damage, int repairs) {
		ItemStack stack = new ItemStack(item);
		stack.setDamageValue(damage);
		DurableComponentItem.setRepairs(stack, repairs);
		return stack;
	}

	/** Load the bench and top the buffer up; the buffer is refilled every tick by {@link #run}. */
	private static void load(ComponentRepairBenchBlockEntity bench, ItemStack target, ItemStack material) {
		bench.setItem(ComponentRepairBenchBlockEntity.TARGET_SLOT, target);
		bench.setItem(ComponentRepairBenchBlockEntity.MATERIAL_SLOT, material);
	}

	/**
	 * Drive the bench for {@code ticks}, keeping the buffer full.
	 *
	 * <p>The buffer holds 800 EU and a T1 repair costs 5000, so a bench charged once would stall
	 * a fifth of the way in and every completion assertion would fail for the wrong reason. Refilling
	 * each tick stands in for a grid that can actually feed it.
	 */
	private static void run(GameTestHelper helper, ComponentRepairBenchBlockEntity bench, int ticks) {
		for (int i = 0; i < ticks; i++) {
			bench.getEnergyStorage().setAmountUntracked(bench.getEnergyStorage().getCapacity());
			BlockPos p = bench.getBlockPos();
			bench.serverTick(helper.getLevel(), p, helper.getLevel().getBlockState(p));
		}
	}

	private static ItemStack targetOf(ComponentRepairBenchBlockEntity bench) {
		return bench.getItem(ComponentRepairBenchBlockEntity.TARGET_SLOT);
	}

	// --- FUN -----------------------------------------------------------------------------------

	/** TC-RBENCH-001-FUN01: a worn T1 rotor comes out clean, one plate lighter, ceiling down a tenth. */
	public static void repairsWornRotorAndLowersCeiling(GameTestHelper helper) {
		ComponentRepairBenchBlockEntity bench = placeBench(helper);
		load(bench, worn(ModContent.WINDMILL_ROTOR.get(), 500, 0),
				new ItemStack(ModContent.IRON_PLATE.get(), 4));
		run(helper, bench, T1_TICKS);

		ItemStack rotor = targetOf(bench);
		if (rotor.isEmpty()) {
			helper.fail("the rotor vanished — a repair must never consume the component");
			return;
		}
		if (rotor.getDamageValue() != 0) {
			helper.fail("wear not cleared: damage=" + rotor.getDamageValue());
		}
		if (rotor.getMaxDamage() != 800) {
			helper.fail("ceiling should drop 1000 -> 800 (20 % of the ORIGINAL); got " + rotor.getMaxDamage());
		}
		if (DurableComponentItem.repairs(rotor) != 1) {
			helper.fail("repair counter should read 1, got " + DurableComponentItem.repairs(rotor));
		}
		int plates = bench.getItem(ComponentRepairBenchBlockEntity.MATERIAL_SLOT).getCount();
		if (plates != 3) {
			helper.fail("exactly one plate should be consumed; 4 -> " + plates);
		}
		helper.succeed();
	}

	/** TC-RBENCH-001-FUN03: four repairs walk the ladder linearly, not by compounding. */
	public static void ceilingLadderIsLinearAcrossRepeatedRepairs(GameTestHelper helper) {
		ComponentRepairBenchBlockEntity bench = placeBench(helper);
		load(bench, worn(ModContent.WINDMILL_ROTOR.get(), 100, 0),
				new ItemStack(ModContent.IRON_PLATE.get(), 16));

		int[] expected = {800, 600, 400, 200};
		for (int i = 0; i < expected.length; i++) {
			run(helper, bench, T1_TICKS);
			ItemStack rotor = targetOf(bench);
			if (rotor.getMaxDamage() != expected[i]) {
				helper.fail("after " + (i + 1) + " repair(s) the ceiling should be " + expected[i]
						+ " (linear in the original) but was " + rotor.getMaxDamage()
						+ " — compounding would give 800/640/512/410");
				return;
			}
			// Wear it again so the next pass has something to repair.
			rotor.setDamageValue(Math.max(1, rotor.getMaxDamage() / 2));
			bench.setChanged();
		}
		helper.succeed();
	}

	/** TC-RBENCH-001-FUN02: each grade is repaired by its own material at its own price. */
	public static void everyGradeRepairsWithItsOwnMaterial(GameTestHelper helper) {
		ComponentRepairBenchBlockEntity bench = placeBench(helper);
		// Reinforced: 3000 durability, tempered plate, 10 000 EU -> 1250 ticks at 8 EU/t.
		load(bench, worn(ModContent.WINDMILL_ROTOR_REINFORCED.get(), 1500, 0),
				new ItemStack(ModContent.TEMPERED_IRON_PLATE.get(), 4));
		run(helper, bench, 1400);
		ItemStack reinforced = targetOf(bench);
		if (reinforced.getDamageValue() != 0 || reinforced.getMaxDamage() != 2400) {
			helper.fail("reinforced rotor should read damage 0 / ceiling 2400, got "
					+ reinforced.getDamageValue() + " / " + reinforced.getMaxDamage());
			return;
		}
		// Advanced: 6000 durability, electronic circuit, 18 000 EU -> 2250 ticks.
		load(bench, worn(ModContent.WATER_MILL_WHEEL_ADVANCED.get(), 3000, 0),
				new ItemStack(ModContent.ELECTRONIC_CIRCUIT.get(), 4));
		run(helper, bench, 2400);
		ItemStack advanced = targetOf(bench);
		if (advanced.getDamageValue() != 0 || advanced.getMaxDamage() != 4800) {
			helper.fail("advanced wheel should read damage 0 / ceiling 4800, got "
					+ advanced.getDamageValue() + " / " + advanced.getMaxDamage());
			return;
		}
		helper.succeed();
	}

	/** TC-RBENCH-001-FUN05: a missing plate freezes progress; putting it back resumes, not restarts. */
	public static void missingMaterialFreezesProgressRatherThanResetting(GameTestHelper helper) {
		ComponentRepairBenchBlockEntity bench = placeBench(helper);
		load(bench, worn(ModContent.WINDMILL_ROTOR.get(), 500, 0),
				new ItemStack(ModContent.IRON_PLATE.get(), 1));
		run(helper, bench, 400);                       // most of the way through
		bench.setItem(ComponentRepairBenchBlockEntity.MATERIAL_SLOT, ItemStack.EMPTY);
		run(helper, bench, 100);                       // stalled
		bench.setItem(ComponentRepairBenchBlockEntity.MATERIAL_SLOT,
				new ItemStack(ModContent.IRON_PLATE.get(), 1));
		run(helper, bench, 300);                       // 400 + 300 = 700 > 625 only if progress survived

		ItemStack rotor = targetOf(bench);
		if (rotor.getDamageValue() != 0) {
			helper.fail("progress was thrown away by the stall: the repair never completed "
					+ "(damage still " + rotor.getDamageValue() + ")");
		}
		helper.succeed();
	}

	// --- NEG -----------------------------------------------------------------------------------

	/**
	 * TC-RBENCH-001-NEG01: a component at its repair allowance is refused outright — no EU, no plate.
	 *
	 * <p>The negative control for the whole limit feature: remove the {@code canRepair} guard and this
	 * reddens, because the bench would happily perform a fifth repair.
	 */
	public static void spentComponentIsRefusedWithoutSpendingAnything(GameTestHelper helper) {
		ComponentRepairBenchBlockEntity bench = placeBench(helper);
		int limit = ComponentRepair.maxRepairs(Config.repairBenchMaxDamageDecayPercent);
		ItemStack spent = worn(ModContent.WINDMILL_ROTOR.get(), 400, limit);
		int ceilingBefore = ComponentTier.WINDMILL_ROTOR.maxDamage();
		spent.set(net.minecraft.core.component.DataComponents.MAX_DAMAGE, ceilingBefore);
		load(bench, spent, new ItemStack(ModContent.IRON_PLATE.get(), 4));

		run(helper, bench, T1_TICKS * 2);

		ItemStack rotor = targetOf(bench);
		if (rotor.getDamageValue() == 0) {
			helper.fail("a component at the repair limit was repaired anyway — the guard is gone");
			return;
		}
		if (DurableComponentItem.repairs(rotor) != limit) {
			helper.fail("counter moved past the limit: " + DurableComponentItem.repairs(rotor));
		}
		int plates = bench.getItem(ComponentRepairBenchBlockEntity.MATERIAL_SLOT).getCount();
		if (plates != 4) {
			helper.fail("material was consumed on a refused repair: 4 -> " + plates);
		}
		helper.succeed();
	}

	/** TC-RBENCH-001-NEG02: an intact component is left alone. */
	public static void intactComponentIsNotTouched(GameTestHelper helper) {
		ComponentRepairBenchBlockEntity bench = placeBench(helper);
		load(bench, new ItemStack(ModContent.WINDMILL_ROTOR.get()),
				new ItemStack(ModContent.IRON_PLATE.get(), 4));
		run(helper, bench, T1_TICKS);

		ItemStack rotor = targetOf(bench);
		if (DurableComponentItem.repairs(rotor) != 0) {
			helper.fail("an undamaged component was 'repaired' — the counter moved");
		}
		if (rotor.getMaxDamage() != ComponentTier.WINDMILL_ROTOR.maxDamage()) {
			helper.fail("an undamaged component lost ceiling for nothing: " + rotor.getMaxDamage());
		}
		if (bench.getItem(ComponentRepairBenchBlockEntity.MATERIAL_SLOT).getCount() != 4) {
			helper.fail("material consumed with nothing to repair");
		}
		helper.succeed();
	}

	/** TC-RBENCH-001-NEG03: the plate of another grade does not drive a repair. */
	public static void wrongGradeMaterialIsRejected(GameTestHelper helper) {
		ComponentRepairBenchBlockEntity bench = placeBench(helper);
		// A T1 rotor with the T2 material: recognised by the slot, but not the right one for this grade.
		load(bench, worn(ModContent.WINDMILL_ROTOR.get(), 500, 0),
				new ItemStack(ModContent.TEMPERED_IRON_PLATE.get(), 4));
		run(helper, bench, T1_TICKS);

		ItemStack rotor = targetOf(bench);
		if (rotor.getDamageValue() != 500) {
			helper.fail("repaired with the wrong grade's material (damage " + rotor.getDamageValue() + ")");
		}
		if (bench.getItem(ComponentRepairBenchBlockEntity.MATERIAL_SLOT).getCount() != 4) {
			helper.fail("the wrong material was consumed anyway");
		}
		helper.succeed();
	}

	/** TC-RBENCH-001-NEG04/NEG05: slot filters, by hand and through a face. */
	public static void slotsRejectWhatTheyShould(GameTestHelper helper) {
		ComponentRepairBenchBlockEntity bench = placeBench(helper);
		int target = ComponentRepairBenchBlockEntity.TARGET_SLOT;
		int material = ComponentRepairBenchBlockEntity.MATERIAL_SLOT;

		if (bench.canPlaceItem(target, new ItemStack(Items.STONE))) {
			helper.fail("target slot accepted a foreign item");
		}
		if (bench.canPlaceItem(material, new ItemStack(ModContent.GOLD_PLATE.get()))) {
			helper.fail("material slot accepted a plate that repairs nothing");
		}
		// By hand a spent component IS accepted — the player has to be able to read WHY it is refused.
		ItemStack spent = worn(ModContent.WINDMILL_ROTOR.get(), 400,
				ComponentRepair.maxRepairs(Config.repairBenchMaxDamageDecayPercent));
		if (!bench.canPlaceItem(target, spent)) {
			helper.fail("a spent component must still be insertable by hand, or the GUI can never "
					+ "explain the refusal");
		}
		// Automation is held to the stricter test, or a hopper pair would cycle for ever.
		if (bench.canPlaceItemThroughFace(target, spent, Direction.UP)) {
			helper.fail("automation inserted a component the bench cannot repair");
		}
		if (bench.canPlaceItemThroughFace(target, new ItemStack(ModContent.WINDMILL_ROTOR.get()),
				Direction.UP)) {
			helper.fail("automation inserted an undamaged component — this is the hopper loop");
		}
		if (!bench.canPlaceItemThroughFace(target, worn(ModContent.WINDMILL_ROTOR.get(), 500, 0),
				Direction.UP)) {
			helper.fail("automation could not insert a genuinely repairable component");
		}
		helper.succeed();
	}

	// --- STA: what automation may take out -----------------------------------------------------

	/** TC-RBENCH-001-STA01..03: extraction opens only once the bench is finished with the part. */
	public static void extractionOpensOnlyWhenTheBenchIsDone(GameTestHelper helper) {
		ComponentRepairBenchBlockEntity bench = placeBench(helper);
		int target = ComponentRepairBenchBlockEntity.TARGET_SLOT;
		int material = ComponentRepairBenchBlockEntity.MATERIAL_SLOT;

		load(bench, worn(ModContent.WINDMILL_ROTOR.get(), 500, 0),
				new ItemStack(ModContent.IRON_PLATE.get(), 4));
		if (bench.canTakeItemThroughFace(target, targetOf(bench), Direction.DOWN)) {
			helper.fail("a component still awaiting repair was extractable");
		}
		if (bench.canTakeItemThroughFace(material, bench.getItem(material), Direction.DOWN)) {
			helper.fail("the material slot must never be an output");
		}

		run(helper, bench, T1_TICKS);
		if (!bench.canTakeItemThroughFace(target, targetOf(bench), Direction.DOWN)) {
			helper.fail("a finished component was not extractable — an auto-repair line would jam");
		}

		// A spent component is also "done": the bench has nothing more to offer it.
		bench.setItem(target, worn(ModContent.WINDMILL_ROTOR.get(), 400,
				ComponentRepair.maxRepairs(Config.repairBenchMaxDamageDecayPercent)));
		if (!bench.canTakeItemThroughFace(target, targetOf(bench), Direction.DOWN)) {
			helper.fail("a component at its limit was stuck in the bench");
		}
		helper.succeed();
	}

	// --- PER -----------------------------------------------------------------------------------

	/**
	 * TC-RBENCH-001-PER02: a repaired rotor keeps its lowered ceiling and its counter through the
	 * wind mill's T1 -> T2 evolution (the MOD-211 slot-snapshot path, now carrying stack components).
	 */
	public static void repairedRotorSurvivesMillEvolution(GameTestHelper helper) {
		ComponentRepairBenchBlockEntity bench = placeBench(helper);
		load(bench, worn(ModContent.WINDMILL_ROTOR.get(), 500, 0),
				new ItemStack(ModContent.IRON_PLATE.get(), 4));
		run(helper, bench, T1_TICKS);
		ItemStack repaired = targetOf(bench).copy();
		if (repaired.getMaxDamage() != 800 || DurableComponentItem.repairs(repaired) != 1) {
			helper.fail("rig precondition failed: the rotor was not repaired before the evolution");
			return;
		}

		BlockPos millPos = new BlockPos(3, 2, 1);
		helper.setBlock(millPos, ModContent.WIND_MILL.get());
		WindMillBlockEntity mill = helper.getBlockEntity(millPos, WindMillBlockEntity.class);
		if (mill == null) {
			helper.fail("wind mill block entity missing");
			return;
		}
		mill.setItem(WindMillBlockEntity.ROTOR_SLOT, repaired);
		mill.setItem(WindMillBlockEntity.CHIP_SLOT, new ItemStack(ModContent.ALIGNMENT_CHIP_DAY.get(), 3));
		mill.setEvolveProgressTicks(Config.windMillEvolveTicks - 1);
		AlaGameTestHelper.drive(mill, helper, 40);

		if (!(helper.getLevel().getBlockEntity(helper.absolutePos(millPos))
				instanceof WindMillBlockEntity evolvedStillT1)) {
			// Evolved away from WindMillBlockEntity — read the rotor off whatever is there now.
			if (!(helper.getLevel().getBlockEntity(helper.absolutePos(millPos))
					instanceof dev.alaindustrial.block.entity.MachineBlockEntity evolved)) {
				helper.fail("nothing left at the mill position after evolution");
				return;
			}
			ItemStack carried = evolved.getItem(WindMillBlockEntity.ROTOR_SLOT);
			assertCarried(helper, carried);
			helper.succeed();
			return;
		}
		// Still a plain mill: the evolution did not fire, so the scenario proves nothing.
		helper.fail("the mill never evolved (progress " + evolvedStillT1.getEvolveProgressTicks()
				+ ") — the carry-over assertion would be vacuous");
	}

	/**
	 * TC-RBENCH-001-PER01: the lowered ceiling and the repair counter survive a save/load cycle.
	 *
	 * <p>Both live on the stack as data components — {@code minecraft:max_damage} and
	 * {@code alaindustrial:repair_count} — so this is really asking whether they were registered as
	 * PERSISTENT. A component declared network-only would look perfect all session and quietly reset
	 * every part in the world on restart, handing players free repairs; nothing else in the suite
	 * would notice.
	 */
	public static void repairedPartSurvivesNbtRoundTrip(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryAccess registries = level.registryAccess();
		ComponentRepairBenchBlockEntity bench = placeBench(helper);
		load(bench, worn(ModContent.WINDMILL_ROTOR.get(), 500, 0),
				new ItemStack(ModContent.IRON_PLATE.get(), 4));
		run(helper, bench, T1_TICKS);
		ItemStack before = targetOf(bench);
		if (before.getMaxDamage() != 800 || DurableComponentItem.repairs(before) != 1) {
			helper.fail("rig precondition failed: the rotor was not repaired before the round trip");
			return;
		}

		BlockPos abs = helper.absolutePos(BENCH);
		CompoundTag tag = bench.saveCustomOnly(registries);
		ComponentRepairBenchBlockEntity restored =
				new ComponentRepairBenchBlockEntity(abs, level.getBlockState(abs));
		restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));

		ItemStack after = restored.getItem(ComponentRepairBenchBlockEntity.TARGET_SLOT);
		if (after.isEmpty()) {
			helper.fail("the component did not survive the round trip at all");
			return;
		}
		if (after.getMaxDamage() != 800) {
			helper.fail("the lowered ceiling did not persist: " + after.getMaxDamage()
					+ " (expected 800) — every part would silently reset to full on restart");
		}
		if (DurableComponentItem.repairs(after) != 1) {
			helper.fail("the repair counter did not persist: " + DurableComponentItem.repairs(after)
					+ " (expected 1) — the allowance would reset and repairs become unlimited");
		}
		helper.succeed();
	}

	/** Flowing (non-source) water — the only kind that drives a wheel since MOD-188. */
	private static BlockState flowingWater() {
		return Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 1);
	}

	/**
	 * TC-RBENCH-001-FUN06: a repaired component goes back to work and wears by the ordinary formula.
	 *
	 * <p>The point is that repairing changes only the ceiling — not how the part behaves. A bug that
	 * left the stack in some "repaired" state the wear path skipped would give a part that never wears
	 * out again, which is precisely the outcome the whole decay design exists to prevent.
	 */
	public static void repairedWheelStillWearsInTheMill(GameTestHelper helper) {
		ComponentRepairBenchBlockEntity bench = placeBench(helper);
		load(bench, worn(ModContent.WATER_MILL_WHEEL.get(), 500, 0),
				new ItemStack(ModContent.IRON_PLATE.get(), 4));
		run(helper, bench, T1_TICKS);
		ItemStack repaired = targetOf(bench).copy();
		if (repaired.getDamageValue() != 0 || repaired.getMaxDamage() != 800) {
			helper.fail("rig precondition failed: the wheel was not repaired");
			return;
		}

		BlockPos millPos = new BlockPos(3, 2, 1);
		WaterMillBlockEntity mill = AlaGameTestHelper.place(helper, millPos,
				ModContent.WATER_MILL.get(), WaterMillBlockEntity.class);
		BlockPos front = millPos.relative(Direction.NORTH);
		helper.setBlock(front.above(), flowingWater());
		helper.setBlock(front.below(), flowingWater());
		helper.setBlock(front.relative(Direction.NORTH.getClockWise()), flowingWater());
		helper.setBlock(front.relative(Direction.NORTH.getCounterClockWise()), flowingWater());
		mill.setItem(WaterMillBlockEntity.WHEEL_SLOT, repaired);
		AlaGameTestHelper.drive(mill, helper, 400);

		ItemStack worked = mill.getItem(WaterMillBlockEntity.WHEEL_SLOT);
		if (worked.isEmpty()) {
			helper.fail("the repaired wheel vanished from the mill");
			return;
		}
		if (worked.getDamageValue() <= 0) {
			helper.fail("a repaired wheel did not wear at all while the mill produced EU — repairing "
					+ "must not make a component immortal");
		}
		if (worked.getMaxDamage() != 800) {
			helper.fail("the mill changed the repaired ceiling: " + worked.getMaxDamage());
		}
		if (DurableComponentItem.repairs(worked) != 1) {
			helper.fail("the mill lost the repair counter: " + DurableComponentItem.repairs(worked));
		}
		helper.succeed();
	}

	private static void assertCarried(GameTestHelper helper, ItemStack carried) {
		if (carried.isEmpty()) {
			helper.fail("the repaired rotor was lost in the evolution");
			return;
		}
		if (carried.getMaxDamage() != 800) {
			helper.fail("the lowered ceiling did not survive the evolution: " + carried.getMaxDamage()
					+ " (expected 800) — an evolution would then be a free full repair");
		}
		if (DurableComponentItem.repairs(carried) != 1) {
			helper.fail("the repair counter did not survive the evolution: "
					+ DurableComponentItem.repairs(carried));
		}
	}
}

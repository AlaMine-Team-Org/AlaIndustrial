package dev.alaindustrial.gametest;

import dev.alaindustrial.block.FuelRodAssemblyBlock;
import dev.alaindustrial.block.IrradiatedSoilBlock;
import dev.alaindustrial.block.entity.FuelRodAssemblyBlockEntity;
import dev.alaindustrial.core.radiation.RadiationConfig;
import dev.alaindustrial.core.radiation.RadiationCore;
import dev.alaindustrial.core.radiation.RadiationSources;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The three emitters the radiation model finds by class today (MOD-715, batch 0): a barrel holding uranium,
 * a fuelled rod assembly, and irradiated soil. Each is asked through the collector the sweep calls, with the
 * exact position and strength it must report, and then end to end: a cow two blocks away is irradiated.
 *
 * <p>Pinned before the collectors ask an interface instead of naming the classes: the shielding chest is
 * covered by {@code RadiationScenarios}, the emitters were covered only as "the field is not zero". The
 * radius and the loose-item reach are passed explicitly (3), so no other scenario's sources reach in and no
 * configuration has to be overridden.
 */
public final class RadiationEmitterScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(RadiationEmitterScenarios::barrelOfUraniumEmits, "mod715_radiation_barrel_emits"),
				RosterEntry.of(RadiationEmitterScenarios::fuelledRodAssemblyEmits,
						"mod715_radiation_rod_assembly_emits"),
				RosterEntry.of(RadiationEmitterScenarios::irradiatedSoilEmits,
						"mod715_radiation_irradiated_soil_emits"));

		private Roster() {}
	}

	private static final int RADIUS = 3;
	private static final BlockPos EMITTER = new BlockPos(4, 2, 4);
	private static final BlockPos BYSTANDER = new BlockPos(4, 2, 6);

	private RadiationEmitterScenarios() {
	}

	/** The sources {@code collected} reports at exactly {@code at}. */
	private static List<RadiationSources.Source> at(List<RadiationSources.Source> collected, Vec3 at) {
		List<RadiationSources.Source> out = new ArrayList<>();
		for (RadiationSources.Source source : collected) {
			if (source.at().equals(at)) {
				out.add(source);
			}
		}
		return out;
	}

	private static boolean expectOne(GameTestHelper helper, String what, List<RadiationSources.Source> found,
			int strength) {
		if (found.size() != 1 || found.get(0).strength() != strength) {
			helper.fail(what + ": expected one source of strength " + strength + ", found " + found);
			return false;
		}
		return true;
	}

	/** A cow two blocks from the emitter, in open air, takes a dose. */
	private static void expectExposure(GameTestHelper helper, String what) {
		Cow cow = helper.spawn(EntityTypes.COW, BYSTANDER);
		int exposure = RadiationSources.exposureAt(helper.getLevel(), cow, RADIUS, RADIUS);
		cow.discard();
		if (exposure <= 0) {
			helper.fail(what + " two blocks away irradiates nothing (exposure " + exposure + ")");
			return;
		}
		helper.succeed();
	}

	/**
	 * A vanilla barrel holding refined uranium is an exposed container: it leaks the capped strength.
	 *
	 * @implements MOD-715-RE01 — a barrel holding uranium emits
	 */
	public static void barrelOfUraniumEmits(GameTestHelper helper) {
		helper.setBlock(EMITTER, Blocks.BARREL);
		ItemStack uranium = new ItemStack(ModContent.REFINED_URANIUM.get(), 16);
		helper.getBlockEntity(EMITTER, BarrelBlockEntity.class).setItem(0, uranium.copy());
		Vec3 centre = Vec3.atCenterOf(helper.absolutePos(EMITTER));
		List<RadiationSources.Source> collected = new ArrayList<>();
		RadiationSources.collectContainers(helper.getLevel(), centre, RADIUS, RADIUS, collected);
		int strength = RadiationCore.containerLeak(RadiationSources.strengthOf(uranium),
				RadiationConfig.radiationContainerMaxItems, RadiationConfig.radiationDoseHighPerItem);
		if (strength <= 0 || !expectOne(helper, "barrel", at(collected, centre), strength)) {
			if (strength <= 0) {
				helper.fail("rig error: sixteen refined uranium have no strength to leak");
			}
			return;
		}
		expectExposure(helper, "a barrel of uranium");
	}

	/**
	 * A rod assembly with four fuel rods radiates the per-rod dose times its rods.
	 *
	 * @implements MOD-715-RE02 — a fuelled rod assembly emits
	 */
	public static void fuelledRodAssemblyEmits(GameTestHelper helper) {
		helper.setBlock(EMITTER, ModContent.FUEL_ROD_ASSEMBLY.get().defaultBlockState()
				.setValue(FuelRodAssemblyBlock.RODS, FuelRodAssemblyBlock.MAX_RODS));
		FuelRodAssemblyBlockEntity rack = helper.getBlockEntity(EMITTER, FuelRodAssemblyBlockEntity.class);
		for (int i = 0; i < FuelRodAssemblyBlock.MAX_RODS; i++) {
			rack.insertRod(new ItemStack(ModContent.URANIUM_FUEL_ROD.get()));
		}
		Vec3 centre = Vec3.atCenterOf(helper.absolutePos(EMITTER));
		List<RadiationSources.Source> collected = new ArrayList<>();
		RadiationSources.collectRods(helper.getLevel(), centre, RADIUS, collected);
		if (!rack.hasFuel() || !expectOne(helper, "rod assembly", at(collected, centre),
				RadiationConfig.radiationRodDosePerTick * rack.getRods())) {
			if (!rack.hasFuel()) {
				helper.fail("rig error: the rod assembly holds no fuel after four rods went in");
			}
			return;
		}
		expectExposure(helper, "a fuelled rod assembly");
	}

	/**
	 * A row of three soil cells at full intensity is one source at the row's centre, with the sum of their
	 * doses.
	 *
	 * @implements MOD-715-RE03 — irradiated soil emits
	 */
	public static void irradiatedSoilEmits(GameTestHelper helper) {
		BlockState soil = ModContent.IRRADIATED_SOIL.get().defaultBlockState()
				.setValue(IrradiatedSoilBlock.INTENSITY, IrradiatedSoilBlock.MAX_INTENSITY);
		helper.setBlock(EMITTER.west(), soil);
		helper.setBlock(EMITTER, soil);
		helper.setBlock(EMITTER.east(), soil);
		Vec3 centre = Vec3.atCenterOf(helper.absolutePos(EMITTER));
		List<RadiationSources.Source> collected = new ArrayList<>();
		RadiationSources.collectFallout(helper.getLevel(), centre, RADIUS, collected);
		int strength = 3 * IrradiatedSoilBlock.doseFor(soil);
		if (strength <= 0) {
			helper.fail("rig error: soil at full intensity carries no dose");
			return;
		}
		if (!expectOne(helper, "irradiated soil", at(collected, centre), strength)) {
			return;
		}
		expectExposure(helper, "a patch of irradiated soil");
	}
}

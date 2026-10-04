package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.fluid.BiofuelFluid;
import dev.alaindustrial.fluid.DieselFluid;
import dev.alaindustrial.fluid.FuelOilFluid;
import dev.alaindustrial.fluid.NutrientSolutionFluid;
import dev.alaindustrial.fluid.OilFluid;
import dev.alaindustrial.fluid.SteamFluid;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * L2 characterization of the mod's fluids (MOD-708, batch 0), on both loaders.
 *
 * <p>Every one of the eleven registered fluids is described by one line: the common fluid family it
 * belongs to, its source and flowing forms, whether it is a source, which fluids it counts as itself,
 * its bucket, the block its default state becomes, and whether the loader-neutral {@link ModContent}
 * handles resolve to the very object in the registry. Moving the fluid declarations into one shared list
 * must leave every line unchanged on both loaders.
 *
 * <p>The family is asked by {@code instanceof} against the common classes, never by the concrete class:
 * NeoForge registers its own subclasses today, and the class itself is not behaviour.
 *
 * <p><b>Updated only by hand</b> from the capture this scenario logs on a mismatch, in a commit that names
 * the behaviour change (ADR-032).
 */
public final class FluidDefinitionScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(FluidDefinitionScenarios::fluidDefinitionsMatchTheReference,
						"fluid_definitions_match_reference").ticks(40));

		private Roster() {}
	}

	private FluidDefinitionScenarios() {}

	/** One fluid and the loader-neutral handles that must name it, its bucket and its liquid block. */
	private record Expected(String id, Supplier<? extends Fluid> fluid, Supplier<Item> bucket, Supplier<Block> block) {}

	/** The eleven fluids in registration order, with the {@link ModContent} slots each loader binds. */
	private static List<Expected> fluids() {
		return List.of(
				new Expected("oil", () -> ModContent.OIL.get(), () -> ModContent.OIL_BUCKET.get(),
						() -> ModContent.OIL_BLOCK.get()),
				new Expected("flowing_oil", () -> ModContent.FLOWING_OIL.get(), () -> ModContent.OIL_BUCKET.get(),
						() -> ModContent.OIL_BLOCK.get()),
				new Expected("diesel", () -> ModContent.DIESEL.get(), () -> ModContent.DIESEL_BUCKET.get(),
						() -> ModContent.DIESEL_BLOCK.get()),
				new Expected("flowing_diesel", () -> ModContent.FLOWING_DIESEL.get(),
						() -> ModContent.DIESEL_BUCKET.get(), () -> ModContent.DIESEL_BLOCK.get()),
				new Expected("fuel_oil", () -> ModContent.FUEL_OIL.get(), () -> ModContent.FUEL_OIL_BUCKET.get(),
						() -> ModContent.FUEL_OIL_BLOCK.get()),
				new Expected("flowing_fuel_oil", () -> ModContent.FLOWING_FUEL_OIL.get(),
						() -> ModContent.FUEL_OIL_BUCKET.get(), () -> ModContent.FUEL_OIL_BLOCK.get()),
				new Expected("biofuel", () -> ModContent.BIOFUEL.get(), () -> ModContent.BIOFUEL_BUCKET.get(),
						() -> ModContent.BIOFUEL_BLOCK.get()),
				new Expected("flowing_biofuel", () -> ModContent.FLOWING_BIOFUEL.get(),
						() -> ModContent.BIOFUEL_BUCKET.get(), () -> ModContent.BIOFUEL_BLOCK.get()),
				new Expected("nutrient_solution", () -> ModContent.NUTRIENT_SOLUTION.get(),
						() -> ModContent.NUTRIENT_SOLUTION_BUCKET.get(),
						() -> ModContent.NUTRIENT_SOLUTION_BLOCK.get()),
				new Expected("flowing_nutrient_solution", () -> ModContent.FLOWING_NUTRIENT_SOLUTION.get(),
						() -> ModContent.NUTRIENT_SOLUTION_BUCKET.get(),
						() -> ModContent.NUTRIENT_SOLUTION_BLOCK.get()),
				new Expected("steam", () -> ModContent.STEAM.get(), () -> net.minecraft.world.item.Items.AIR,
						() -> net.minecraft.world.level.block.Blocks.AIR));
	}

	/** The reference: one line per fluid, in the order of {@link #fluids()}. */
	static final List<String> EXPECTED = List.of(
			"oil family=OilFluid source=alaindustrial:oil flowing=alaindustrial:flowing_oil isSource=true"
					+ " same=alaindustrial:flowing_oil,alaindustrial:oil bucket=alaindustrial:oil_bucket"
					+ " block=alaindustrial:oil handles=bound",
			"flowing_oil family=OilFluid source=alaindustrial:oil flowing=alaindustrial:flowing_oil isSource=false"
					+ " same=alaindustrial:flowing_oil,alaindustrial:oil bucket=alaindustrial:oil_bucket"
					+ " block=alaindustrial:oil handles=bound",
			"diesel family=DieselFluid source=alaindustrial:diesel flowing=alaindustrial:flowing_diesel isSource=true"
					+ " same=alaindustrial:diesel,alaindustrial:flowing_diesel bucket=alaindustrial:diesel_bucket"
					+ " block=alaindustrial:diesel handles=bound",
			"flowing_diesel family=DieselFluid source=alaindustrial:diesel flowing=alaindustrial:flowing_diesel"
					+ " isSource=false same=alaindustrial:diesel,alaindustrial:flowing_diesel"
					+ " bucket=alaindustrial:diesel_bucket block=alaindustrial:diesel handles=bound",
			"fuel_oil family=FuelOilFluid source=alaindustrial:fuel_oil flowing=alaindustrial:flowing_fuel_oil"
					+ " isSource=true same=alaindustrial:flowing_fuel_oil,alaindustrial:fuel_oil"
					+ " bucket=alaindustrial:fuel_oil_bucket block=alaindustrial:fuel_oil handles=bound",
			"flowing_fuel_oil family=FuelOilFluid source=alaindustrial:fuel_oil flowing=alaindustrial:flowing_fuel_oil"
					+ " isSource=false same=alaindustrial:flowing_fuel_oil,alaindustrial:fuel_oil"
					+ " bucket=alaindustrial:fuel_oil_bucket block=alaindustrial:fuel_oil handles=bound",
			"biofuel family=BiofuelFluid source=alaindustrial:biofuel flowing=alaindustrial:flowing_biofuel"
					+ " isSource=true same=alaindustrial:biofuel,alaindustrial:flowing_biofuel"
					+ " bucket=alaindustrial:biofuel_bucket block=alaindustrial:biofuel handles=bound",
			"flowing_biofuel family=BiofuelFluid source=alaindustrial:biofuel flowing=alaindustrial:flowing_biofuel"
					+ " isSource=false same=alaindustrial:biofuel,alaindustrial:flowing_biofuel"
					+ " bucket=alaindustrial:biofuel_bucket block=alaindustrial:biofuel handles=bound",
			"nutrient_solution family=NutrientSolutionFluid source=alaindustrial:nutrient_solution"
					+ " flowing=alaindustrial:flowing_nutrient_solution isSource=true"
					+ " same=alaindustrial:flowing_nutrient_solution,alaindustrial:nutrient_solution"
					+ " bucket=alaindustrial:nutrient_solution_bucket block=alaindustrial:nutrient_solution"
					+ " handles=bound",
			"flowing_nutrient_solution family=NutrientSolutionFluid source=alaindustrial:nutrient_solution"
					+ " flowing=alaindustrial:flowing_nutrient_solution isSource=false"
					+ " same=alaindustrial:flowing_nutrient_solution,alaindustrial:nutrient_solution"
					+ " bucket=alaindustrial:nutrient_solution_bucket block=alaindustrial:nutrient_solution"
					+ " handles=bound",
			"steam family=SteamFluid source=- flowing=- isSource=true same=alaindustrial:steam"
					+ " bucket=minecraft:air block=minecraft:air handles=bound");

	/** Every line this loader produces, in the order of {@link #fluids()}. */
	static List<String> capture() {
		List<String> lines = new ArrayList<>();
		for (Expected entry : fluids()) {
			lines.add(describe(entry));
		}
		return lines;
	}

	private static String describe(Expected entry) {
		Identifier key = Industrialization.id(entry.id());
		if (!BuiltInRegistries.FLUID.containsKey(key)) {
			return entry.id() + " unregistered";
		}
		Fluid fluid = BuiltInRegistries.FLUID.getValue(key);
		String source = "-";
		String flowing = "-";
		if (fluid instanceof FlowingFluid flowingFluid) {
			source = fluidId(flowingFluid.getSource());
			flowing = fluidId(flowingFluid.getFlowing());
		}
		List<String> same = new ArrayList<>();
		for (Fluid other : BuiltInRegistries.FLUID) {
			if (fluid.isSame(other)) {
				same.add(fluidId(other));
			}
		}
		same.sort(null);
		boolean handles = entry.fluid().get() == fluid && entry.bucket().get() == fluid.getBucket()
				&& entry.block().get() == fluid.defaultFluidState().createLegacyBlock().getBlock();
		return entry.id() + " family=" + family(fluid) + " source=" + source + " flowing=" + flowing
				+ " isSource=" + fluid.defaultFluidState().isSource() + " same=" + String.join(",", same)
				+ " bucket=" + BuiltInRegistries.ITEM.getKey(fluid.getBucket())
				+ " block=" + BuiltInRegistries.BLOCK.getKey(fluid.defaultFluidState().createLegacyBlock().getBlock())
				+ " handles=" + (handles ? "bound" : "DIFFERENT")
				+ (fluid.isSame(Fluids.WATER) ? " sameAsWater" : "");
	}

	private static String family(Fluid fluid) {
		if (fluid instanceof OilFluid) {
			return "OilFluid";
		}
		if (fluid instanceof DieselFluid) {
			return "DieselFluid";
		}
		if (fluid instanceof FuelOilFluid) {
			return "FuelOilFluid";
		}
		if (fluid instanceof BiofuelFluid) {
			return "BiofuelFluid";
		}
		if (fluid instanceof NutrientSolutionFluid) {
			return "NutrientSolutionFluid";
		}
		if (fluid instanceof SteamFluid) {
			return "SteamFluid";
		}
		return "other";
	}

	private static String fluidId(Fluid fluid) {
		return String.valueOf(BuiltInRegistries.FLUID.getKey(fluid));
	}

	/**
	 * @implements MOD-708-FLD01 — every fluid keeps its family, source and flowing forms, bucket, liquid
	 *     block and the {@code ModContent} handles bound to the registered object, on both loaders
	 */
	public static void fluidDefinitionsMatchTheReference(GameTestHelper helper) {
		List<String> actual = capture();
		if (!actual.equals(EXPECTED)) {
			Industrialization.LOGGER.info("MOD-708 fluid definitions differ ({} lines)\n{}", actual.size(),
					String.join("\n", actual));
			int index = 0;
			while (index < Math.min(actual.size(), EXPECTED.size()) && actual.get(index).equals(EXPECTED.get(index))) {
				index++;
			}
			helper.fail("MOD-708: fluid definition differs at line " + (index + 1) + ": expected '"
					+ (index < EXPECTED.size() ? EXPECTED.get(index) : "<end>") + "', got '"
					+ (index < actual.size() ? actual.get(index) : "<end>") + "'. The full capture is in the log.");
			return;
		}
		helper.succeed();
	}
}

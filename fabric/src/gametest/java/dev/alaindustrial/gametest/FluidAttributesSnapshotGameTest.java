package dev.alaindustrial.gametest;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Fabric entry point for the fluid-attribute characterization (MOD-708, batch 0). The comparison is
 * loader-neutral ({@link FluidAttributesSnapshotScenarios}); the probe and the reference are Fabric's:
 * the Transfer API {@code FluidVariantAttributes} (verified by javap against fabric-transfer-api-v1 8.0.25
 * for 26.3), an API NeoForge does not have.
 *
 * <p><b>The reference pins the behaviour since MOD-728:</b> every fluid, still and flowing, has an
 * attribute handler that states the viscosity, temperature and name its NeoForge {@code FluidType} states;
 * steam is named by {@code fluid.alaindustrial.steam}. Before MOD-728 only crude oil had a handler and the
 * other five reported the Transfer API defaults. Next to the literal, {@link #fluidAttributesFollowTheManifest}
 * checks the same facts against {@code ModFluidsManifest} itself.
 */
public class FluidAttributesSnapshotGameTest {

	private static final FluidAttributesSnapshotScenarios.Probe PROBE = (fluid, helper) -> {
		FluidVariant variant = FluidVariant.of(fluid);
		Component name = FluidVariantAttributes.getName(variant);
		return "handler=" + (FluidVariantAttributes.getHandler(fluid) != null ? "custom" : "default")
				+ " viscosity=" + FluidVariantAttributes.getViscosity(variant, helper.getLevel())
				+ " temperature=" + FluidVariantAttributes.getTemperature(variant)
				+ " luminance=" + FluidVariantAttributes.getLuminance(variant)
				+ " lighterThanAir=" + FluidVariantAttributes.isLighterThanAir(variant)
				+ " name=" + (name.getContents() instanceof TranslatableContents key ? key.getKey() : name.getString())
				+ " fill=" + FluidVariantAttributes.getFillSound(variant).location()
				+ " empty=" + FluidVariantAttributes.getEmptySound(variant).location();
	};

	private static final String BUCKET = " fill=minecraft:item.bucket.fill empty=minecraft:item.bucket.empty";
	private static final String TAIL = " luminance=0 lighterThanAir=false";

	private static String custom(int viscosity, int temperature) {
		return "handler=custom viscosity=" + viscosity + " temperature=" + temperature + TAIL;
	}

	private static final String OIL = custom(3000, 300);
	private static final String DIESEL = custom(1200, 300);
	private static final String FUEL_OIL = custom(2400, 300);
	private static final String BIOFUEL = custom(1000, 300);
	private static final String NUTRIENT = custom(900, 300);
	private static final String STEAM = custom(200, 373);

	/** The reference: one line per fluid, in registration order. */
	static final List<String> EXPECTED = List.of(
			"oil " + OIL + " name=block.alaindustrial.oil" + BUCKET,
			"flowing_oil " + OIL + " name=block.alaindustrial.oil" + BUCKET,
			"diesel " + DIESEL + " name=block.alaindustrial.diesel" + BUCKET,
			"flowing_diesel " + DIESEL + " name=block.alaindustrial.diesel" + BUCKET,
			"fuel_oil " + FUEL_OIL + " name=block.alaindustrial.fuel_oil" + BUCKET,
			"flowing_fuel_oil " + FUEL_OIL + " name=block.alaindustrial.fuel_oil" + BUCKET,
			"biofuel " + BIOFUEL + " name=block.alaindustrial.biofuel" + BUCKET,
			"flowing_biofuel " + BIOFUEL + " name=block.alaindustrial.biofuel" + BUCKET,
			"nutrient_solution " + NUTRIENT + " name=block.alaindustrial.nutrient_solution" + BUCKET,
			"flowing_nutrient_solution " + NUTRIENT + " name=block.alaindustrial.nutrient_solution" + BUCKET,
			"steam " + STEAM + " name=fluid.alaindustrial.steam" + BUCKET);

	/** The Transfer API's answers in {@code ModFluidsManifest} terms. */
	private static final FluidAttributesSnapshotScenarios.FactsProbe FACTS = (fluid, helper) -> {
		FluidVariant variant = FluidVariant.of(fluid);
		Component name = FluidVariantAttributes.getName(variant);
		return new FluidAttributesSnapshotScenarios.Facts(
				FluidVariantAttributes.getViscosity(variant, helper.getLevel()),
				FluidVariantAttributes.getTemperature(variant), FluidVariantAttributes.isLighterThanAir(variant),
				name.getContents() instanceof TranslatableContents key ? key.getKey() : name.getString());
	};

	/**
	 * @implements MOD-708-FLD02 — Fabric reports the pinned fluid attributes for every mod fluid
	 */
	@GameTest
	public void fluidAttributesMatchTheReference(GameTestHelper helper) {
		FluidAttributesSnapshotScenarios.attributesMatchTheReference(helper, PROBE, EXPECTED);
	}

	/**
	 * @implements MOD-728-AC1 — every Fabric fluid reports the viscosity, temperature, lighter-than-air sign
	 *     and name key of its {@code ModFluidsManifest} entry, the entry NeoForge's fluid type is built from
	 */
	@GameTest
	public void fluidAttributesFollowTheManifest(GameTestHelper helper) {
		FluidAttributesSnapshotScenarios.attributesFollowTheManifest(helper, FACTS);
	}
}

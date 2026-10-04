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
 * <p><b>The reference pins today's behaviour, including two known discrepancies.</b> Only crude oil (still
 * and flowing) has an attribute handler on Fabric, so its viscosity is 3000; diesel, fuel oil, biofuel,
 * nutrient solution and steam fall back to the Transfer API defaults (viscosity 1000, temperature 300)
 * while their NeoForge {@code FluidType}s state their own numbers. And steam, which has no block, is named
 * by the untranslated key {@code block.alaindustrial.steam}. Both are a defect with its own task; moving the
 * fluids into a shared list must not change either in any direction.
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
	private static final String OIL = "handler=custom viscosity=3000 temperature=300 luminance=0 lighterThanAir=false";
	private static final String DEFAULT =
			"handler=default viscosity=1000 temperature=300 luminance=0 lighterThanAir=false";

	/** The reference: one line per fluid, in registration order. */
	static final List<String> EXPECTED = List.of(
			"oil " + OIL + " name=block.alaindustrial.oil" + BUCKET,
			"flowing_oil " + OIL + " name=block.alaindustrial.oil" + BUCKET,
			"diesel " + DEFAULT + " name=block.alaindustrial.diesel" + BUCKET,
			"flowing_diesel " + DEFAULT + " name=block.alaindustrial.diesel" + BUCKET,
			"fuel_oil " + DEFAULT + " name=block.alaindustrial.fuel_oil" + BUCKET,
			"flowing_fuel_oil " + DEFAULT + " name=block.alaindustrial.fuel_oil" + BUCKET,
			"biofuel " + DEFAULT + " name=block.alaindustrial.biofuel" + BUCKET,
			"flowing_biofuel " + DEFAULT + " name=block.alaindustrial.biofuel" + BUCKET,
			"nutrient_solution " + DEFAULT + " name=block.alaindustrial.nutrient_solution" + BUCKET,
			"flowing_nutrient_solution " + DEFAULT + " name=block.alaindustrial.nutrient_solution" + BUCKET,
			"steam " + DEFAULT + " name=block.alaindustrial.steam" + BUCKET);

	/**
	 * @implements MOD-708-FLD02 — Fabric reports today's fluid attributes for every mod fluid: crude oil's
	 *     handler, the Transfer API defaults for the other five (the known discrepancy, pinned as is)
	 */
	@GameTest
	public void fluidAttributesMatchTheReference(GameTestHelper helper) {
		FluidAttributesSnapshotScenarios.attributesMatchTheReference(helper, PROBE, EXPECTED);
	}
}

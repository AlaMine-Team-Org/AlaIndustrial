package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Fabric registration for the loader-neutral MOD-483 skill-effect scenarios. */
public final class SkillEffectGameTest {

	@GameTest
	public void mod483FrugalStrokeDiscountsWork(GameTestHelper helper) {
		SkillEffectScenarios.frugalStrokeDiscountsWork(helper);
	}

	@GameTest
	public void mod483DiscountNeverMintsEnergy(GameTestHelper helper) {
		SkillEffectScenarios.discountNeverMintsEnergy(helper);
	}

	@GameTest
	public void mod483RecuperatorRefundsIntoThePack(GameTestHelper helper) {
		SkillEffectScenarios.recuperatorRefundsIntoThePack(helper);
	}

	@GameTest
	public void mod483WithoutSkillsNothingChanges(GameTestHelper helper) {
		SkillEffectScenarios.withoutSkillsNothingChanges(helper);
	}
}

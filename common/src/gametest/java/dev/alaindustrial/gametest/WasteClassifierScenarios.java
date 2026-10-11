package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.WasteClassifier;
import dev.alaindustrial.block.entity.WasteClassifier.WasteProfile;
import dev.alaindustrial.core.waste.WasteFraction;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Recycler's classifier (MOD-145), asked directly: which fraction and how much mass one item
 * carries. Until MOD-655 nothing tested it — the recycler scenarios saw it only through the grade of a
 * whole batch, which many wrong answers share. It needs the tags the world has bound, so it runs as a
 * world gametest on both lanes rather than on the Minecraft-free L1 lane.
 */
public final class WasteClassifierScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(WasteClassifierScenarios::sawdustIsCombustible,
								"waste_classifier_sawdust_is_combustible")
						.ticks(20),
				RosterEntry.of(WasteClassifierScenarios::eachRuleOfTheChainAnswers,
								"waste_classifier_each_rule_of_the_chain_answers")
						.ticks(20));

		private Roster() {}
	}

	private WasteClassifierScenarios() {
	}

	/**
	 * Sawdust is combustible, weighed as a plain item. It reaches that by the {@code c:dusts/wood} tag,
	 * not by being fuel; without the rule it would fall through to OTHER with mass 1.
	 *
	 * @implements MOD-655 — the classifier files sawdust (#c:dusts/wood) as COMBUSTIBLE, mass of a plain item.
	 */
	public static void sawdustIsCombustible(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		expect(problems, "sawdust", new ItemStack(ModContent.SAWDUST.get()), WasteClassifier.MASS_ITEM,
				WasteFraction.COMBUSTIBLE);
		report(helper, problems);
	}

	/**
	 * One item per rule of the chain, in its order: gear before tags, the tag rules, soil before sound, sound,
	 * and the fallback — and the empty stack. A rule moved or dropped answers one of these wrong.
	 *
	 * @implements MOD-655 — every rule of the classifier chain gives its documented fraction and mass.
	 */
	public static void eachRuleOfTheChainAnswers(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		expect(problems, "empty", ItemStack.EMPTY, 0, WasteFraction.OTHER);
		// 1. gear: a damageable tool is metal scrap, whatever it is made of.
		expect(problems, "wooden pickaxe", new ItemStack(Items.WOODEN_PICKAXE), WasteClassifier.MASS_GEAR,
				WasteFraction.METAL);
		// 2. tags: c:ingots, planks/logs/coals, dirt/sand.
		expect(problems, "iron ingot", new ItemStack(Items.IRON_INGOT), WasteClassifier.MASS_ITEM,
				WasteFraction.METAL);
		expect(problems, "oak planks", new ItemStack(Items.OAK_PLANKS), WasteClassifier.MASS_BLOCK,
				WasteFraction.COMBUSTIBLE);
		expect(problems, "coal", new ItemStack(Items.COAL), WasteClassifier.MASS_ITEM, WasteFraction.COMBUSTIBLE);
		expect(problems, "sand", new ItemStack(Items.SAND), WasteClassifier.MASS_BLOCK, WasteFraction.MINERAL);
		// 3. a block with no tag: soil before sound (grass sounds like foliage), then the sound.
		expect(problems, "grass block", new ItemStack(Items.GRASS_BLOCK), WasteClassifier.MASS_BLOCK,
				WasteFraction.MINERAL);
		expect(problems, "cobblestone", new ItemStack(Items.COBBLESTONE), WasteClassifier.MASS_BLOCK,
				WasteFraction.MINERAL);
		expect(problems, "anvil", new ItemStack(Items.ANVIL), WasteClassifier.MASS_BLOCK, WasteFraction.METAL);
		// 4. fallback: a plain item no rule knows.
		expect(problems, "paper", new ItemStack(Items.PAPER), WasteClassifier.MASS_UNKNOWN, WasteFraction.OTHER);
		report(helper, problems);
	}

	private static void expect(List<String> problems, String what, ItemStack stack, int mass,
			WasteFraction fraction) {
		WasteProfile got = WasteClassifier.classify(stack);
		if (got.mass() != mass || got.fraction() != fraction) {
			problems.add(what + ": got " + got.fraction() + " mass " + got.mass() + ", expected " + fraction + " mass "
					+ mass);
		}
	}

	private static void report(GameTestHelper helper, List<String> problems) {
		if (!problems.isEmpty()) {
			helper.fail("WasteClassifier: " + String.join("; ", problems));
			return;
		}
		helper.succeed();
	}
}

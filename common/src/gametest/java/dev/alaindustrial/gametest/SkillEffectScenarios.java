package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.item.wearable.EnergyPackItem;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.skill.PlayerSkills;
import dev.alaindustrial.skill.SkillBranch;
import dev.alaindustrial.skill.SkillBuild;
import dev.alaindustrial.skill.SkillSlot;
import dev.alaindustrial.skill.SkillStore;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * What the skills actually do to a live world (MOD-483, suite TC-SKILL-EFFECT-001).
 *
 * <p>These exist because of the exact defect they would have caught. The tree's rules are unit-tested
 * and the wheel's arithmetic is unit-tested, but the <b>wiring</b> — the line where a skill meets the
 * number it changes — was only ever read. Reading found a dupe (a discounted Energy Pack delivered
 * more EU than it debited, minting energy several times a second) and a skill wired to the wrong
 * charger; both were invisible to every existing test. So the wiring gets tests of its own, and they
 * assert on the two properties that matter: <b>the discount applies to work</b>, and
 * <b>energy is conserved when it does not</b>.
 *
 * <p>The bodies are loader-neutral, but the three that GRANT a skill run on Fabric only: writing a
 * per-player attachment makes NeoForge sync it to the holder on the spot, down a connection a
 * vanilla gametest mock does not have. The reason is recorded in the parity gate's allow-list.
 * The control case writes nothing and runs on both.
 */
public final class SkillEffectScenarios {

	private SkillEffectScenarios() {
	}

	/**
	 * A mock player in SURVIVAL, declared as {@link ServerPlayer}.
	 *
	 * <p>Survival matters: {@code ItemEnergy.spend} deliberately writes nothing for a player with
	 * infinite materials, so a creative mock would make every assertion below vacuously true. The cast
	 * is needed because the helper declares the weaker {@code Player} return type.
	 */
	private static ServerPlayer survivalPlayer(GameTestHelper helper) {
		return (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
	}

	/** A drill charged to full, so a spend is always affordable. */
	private static ItemStack fullDrill() {
		ItemStack drill = new ItemStack(ModContent.ELECTRIC_DRILL.get());
		ItemEnergy.set(drill, ItemEnergy.capacity(drill));
		return drill;
	}

	private static void grant(ServerPlayer player, SkillBranch branch, SkillSlot... slots) {
		SkillBuild build = SkillBuild.EMPTY;
		for (SkillSlot slot : slots) {
			build = build.with(branch, slot);
		}
		SkillStore.set(player, new PlayerSkills(build));
	}

	/**
	 * Frugal Stroke takes its cut off a tool action, and rounds UP.
	 *
	 * <p>The rounding is asserted, not assumed: a cheap action is where a percentage does the most
	 * damage, and rounding the other way would hand the 2 EU magnet a 50 % discount while the drill got
	 * its intended 10 %.
	 */
	public static void frugalStrokeDiscountsWork(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		ItemStack drill = fullDrill();
		long before = ItemEnergy.get(drill);

		ItemEnergy.spend(drill, Config.electricDrillEuPerBlock, player);
		long fullPrice = before - ItemEnergy.get(drill);
		helper.assertValueEqual(fullPrice, (long) Config.electricDrillEuPerBlock,
				"a drill with no skills pays the listed price");

		grant(player, SkillBranch.ENERGY, SkillSlot.IN);
		ItemStack second = fullDrill();
		long start = ItemEnergy.get(second);
		ItemEnergy.spend(second, Config.electricDrillEuPerBlock, player);
		long discounted = start - ItemEnergy.get(second);

		helper.assertTrue(discounted < fullPrice, "Frugal Stroke must make the action cheaper");
		long expected = Math.max(1L, Math.ceilDiv(Config.electricDrillEuPerBlock * 90L, 100L));
		helper.assertValueEqual(discounted, expected, "Frugal Stroke is 10% off, rounded up");
		helper.succeed();
	}

	/**
	 * The discount must never reach a carrier — the regression test for a real dupe.
	 *
	 * <p>An Energy Pack pays for what it hands out through the same {@code spend} a drill uses. While
	 * the discount applied there, the pack delivered a hundred and debited ninety: ten EU per transfer
	 * out of nothing, several times a second. The assertion is conservation — the pack loses exactly
	 * what it was asked to lose.
	 */
	public static void discountNeverMintsEnergy(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		grant(player, SkillBranch.ENERGY, SkillSlot.IN, SkillSlot.A2);

		ItemStack pack = new ItemStack(ModContent.ENERGY_PACK.get());
		ItemEnergy.set(pack, ItemEnergy.capacity(pack));
		long before = ItemEnergy.get(pack);
		long payout = 500L;

		ItemEnergy.spend(pack, payout, player);

		long lost = before - ItemEnergy.get(pack);
		helper.assertValueEqual(lost, payout,
				"a carrier pays the full price: discounting its payout would create EU from nothing");
		helper.succeed();
	}

	/**
	 * Recuperator returns part of a tool's spend to the worn pack — and to the pack only.
	 *
	 * <p>Refunding into the tool itself would make the node a second copy of the entry discount, so the
	 * test checks where the energy landed, not merely that some appeared.
	 */
	public static void recuperatorRefundsIntoThePack(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		grant(player, SkillBranch.ENERGY, SkillSlot.A2);

		ItemStack pack = new ItemStack(ModContent.ENERGY_PACK.get());
		ItemEnergy.set(pack, 0);
		player.setItemSlot(EquipmentSlot.CHEST, pack);
		helper.assertTrue(player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof EnergyPackItem,
				"the pack must be worn for the refund to have a destination");

		ItemStack drill = fullDrill();
		long drillBefore = ItemEnergy.get(drill);
		ItemEnergy.spend(drill, Config.electricDrillEuPerBlock, player);

		long spent = drillBefore - ItemEnergy.get(drill);
		long refunded = ItemEnergy.get(player.getItemBySlot(EquipmentSlot.CHEST));
		helper.assertTrue(refunded > 0, "Recuperator must put something back into the pack");
		helper.assertTrue(refunded < spent, "the refund is a fraction of the spend, never the whole of it");
		helper.succeed();
	}

	/** With no skills at all, nothing about a spend changes — the control the others are measured against. */
	public static void withoutSkillsNothingChanges(GameTestHelper helper) {
		// Nothing is written to the player on purpose: a fresh one HAS no skills, and asking for them
		// must not create any. That is the state this asserts, and it is also why this one case runs on
		// both lanes while its three siblings cannot (see the class comment).
		ServerPlayer player = survivalPlayer(helper);

		ItemStack drill = fullDrill();
		long before = ItemEnergy.get(drill);
		ItemEnergy.spend(drill, Config.electricDrillEuPerBlock, player);

		helper.assertValueEqual(before - ItemEnergy.get(drill), (long) Config.electricDrillEuPerBlock,
				"an unskilled player pays exactly the configured price");
		helper.succeed();
	}
}

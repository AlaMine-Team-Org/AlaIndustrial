package dev.alaindustrial.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.advancement.MutationCompletedTrigger;
import dev.alaindustrial.advancement.NetworkEnergizedTrigger;
import dev.alaindustrial.advancement.ReactorMilestone;
import dev.alaindustrial.advancement.ReactorMilestoneTrigger;
import dev.alaindustrial.mutation.MutationGrade;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModCriteria;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static dev.alaindustrial.gametest.ReactorAdvancementScenarios.earned;

/**
 * L2 characterization of the mod's three advancement criteria (MOD-704, batch 0), on both loaders: each
 * trigger, fired through {@link ModCriteria}, awards the advancements of {@code data/alaindustrial/advancement/}
 * that name it — and only those whose conditions match — and every {@code TriggerInstance} codec reads and
 * writes its JSON with and without a {@code player} condition.
 *
 * <p>The {@code player} field is the one part of these triggers whose type differs between the Minecraft
 * lines (a list of loot conditions on 26.2, one condition holder on 26.3), and MOD-704 batch 7 is about to
 * move its codec into a seam. The codec case therefore offers both JSON shapes and demands that at least
 * one of them reads with a player condition and writes back to exactly itself; which shape a line accepts is
 * logged and recorded in research.md, not hard-coded here, so the scenario is the same text on both lines.
 */
public final class AdvancementCriteriaScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(AdvancementCriteriaScenarios::networkEnergizedAwardsItsAdvancement,
								"criteria_network_energized_awards").ticks(20, 40),
				RosterEntry.of(AdvancementCriteriaScenarios::mutationCompletedAwardsByConditions,
								"criteria_mutation_completed_awards").ticks(20, 40),
				RosterEntry.of(AdvancementCriteriaScenarios::reactorMilestoneAwardsEachStep,
								"criteria_reactor_milestone_awards").ticks(20, 40),
				RosterEntry.of(AdvancementCriteriaScenarios::triggerInstanceCodecsRoundTrip,
								"criteria_trigger_instance_codecs_round_trip").ticks(20, 40));

		private Roster() {}
	}

	private AdvancementCriteriaScenarios() {}

	/**
	 * @implements MOD-704-AC01 — firing {@code network_energized} through {@code ModCriteria} awards
	 *     {@code energized_network}, which nothing tested before.
	 */
	public static void networkEnergizedAwardsItsAdvancement(GameTestHelper helper) {
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		if (earned(helper, player, "energized_network")) {
			helper.fail("fixture error: a fresh player already has energized_network");
		}
		ModCriteria.NETWORK_ENERGIZED.get().trigger(player);
		if (!earned(helper, player, "energized_network")) {
			helper.fail("network_energized fired, but energized_network was not awarded");
		}
		helper.succeed();
	}

	/**
	 * @implements MOD-704-AC02 — {@code mutation_completed} awards {@code first_mutation} on any take, adds
	 *     {@code legendary_mutation} only for a legendary grade and {@code first_creation} only for an item of
	 *     {@code #alaindustrial:mutation_created}.
	 */
	public static void mutationCompletedAwardsByConditions(GameTestHelper helper) {
		MutationCompletedTrigger trigger = ModCriteria.MUTATION_COMPLETED.get();
		ServerPlayer plain = AlaGameTestHelper.survivalPlayer(helper);
		trigger.trigger(plain, new ItemStack(Items.STONE), MutationGrade.COMMON);
		expect(helper, plain, "first_mutation", true, "after a common take of stone");
		expect(helper, plain, "legendary_mutation", false, "after a common take");
		expect(helper, plain, "first_creation", false, "after a take of stone");
		trigger.trigger(plain, new ItemStack(Items.STONE), MutationGrade.LEGENDARY);
		expect(helper, plain, "legendary_mutation", true, "after a legendary take");

		ServerPlayer creator = AlaGameTestHelper.survivalPlayer(helper);
		trigger.trigger(creator, new ItemStack(ModContent.IRRADIATED_DIAMOND.get()), MutationGrade.COMMON);
		expect(helper, creator, "first_creation", true, "after a take of an irradiated diamond");
		expect(helper, creator, "legendary_mutation", false, "after a common take");
		helper.succeed();
	}

	/** Milestone → the advancement whose criterion names it, in the order a reactor reaches them. */
	private static final List<String[]> MILESTONE_ADVANCEMENTS = List.of(
			new String[] {"room_sealed", "reactor_room"},
			new String[] {"power", "reactor_power"},
			new String[] {"steam", "reactor_steam"},
			new String[] {"meltdown", "reactor_meltdown"},
			new String[] {"blast", "reactor_blast"});

	/**
	 * @implements MOD-704-AC03 — each {@code reactor_milestone} step awards exactly its own advancement:
	 *     before the step it is not earned, after it it is, and the later steps are still not earned.
	 */
	public static void reactorMilestoneAwardsEachStep(GameTestHelper helper) {
		ReactorMilestoneTrigger trigger = ModCriteria.REACTOR_MILESTONE.get();
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		if (MILESTONE_ADVANCEMENTS.size() != ReactorMilestone.values().length) {
			helper.fail("the table covers " + MILESTONE_ADVANCEMENTS.size() + " milestones, the enum has "
					+ ReactorMilestone.values().length);
		}
		for (int i = 0; i < MILESTONE_ADVANCEMENTS.size(); i++) {
			String[] row = MILESTONE_ADVANCEMENTS.get(i);
			ReactorMilestone milestone = ReactorMilestone.byId(row[0]);
			if (milestone == null) {
				helper.fail("no reactor milestone with the id " + row[0]);
				return;
			}
			expect(helper, player, row[1], false, "before " + row[0]);
			trigger.trigger(player, milestone);
			expect(helper, player, row[1], true, "after " + row[0]);
			for (int later = i + 1; later < MILESTONE_ADVANCEMENTS.size(); later++) {
				expect(helper, player, MILESTONE_ADVANCEMENTS.get(later)[1], false, "after only " + row[0]);
			}
		}
		helper.succeed();
	}

	/** The player condition in the 26.3 shape (one condition holder) and in the 26.2 shape (a list). */
	private static final List<String> PLAYER_SHAPES = List.of(
			"{\"type\":\"minecraft:killed_by_player\"}",
			"[{\"condition\":\"minecraft:killed_by_player\"}]");

	/**
	 * @implements MOD-704-AC04 — the {@code TriggerInstance} codec of each criterion writes back exactly the
	 *     JSON it read: without a {@code player} condition, and with one in the JSON shape this line reads.
	 */
	public static void triggerInstanceCodecsRoundTrip(GameTestHelper helper) {
		DynamicOps<JsonElement> ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
		List<String> problems = new ArrayList<>();
		problems.addAll(roundTrip(ops, "network_energized", NetworkEnergizedTrigger.TriggerInstance.CODEC,
				NetworkEnergizedTrigger.TriggerInstance::player, "", ""));
		problems.addAll(roundTrip(ops, "mutation_completed", MutationCompletedTrigger.TriggerInstance.CODEC,
				MutationCompletedTrigger.TriggerInstance::player, ",\"grade\":\"legendary\"",
				"\"grade\":\"legendary\""));
		problems.addAll(roundTrip(ops, "reactor_milestone", ReactorMilestoneTrigger.TriggerInstance.CODEC,
				ReactorMilestoneTrigger.TriggerInstance::player, ",\"milestone\":\"steam\"",
				"\"milestone\":\"steam\""));
		if (!problems.isEmpty()) {
			helper.fail(String.join("; ", problems));
			return;
		}
		helper.succeed();
	}

	/**
	 * Problems with one codec. {@code rest} is the instance's other fields as a JSON tail (leading comma),
	 * {@code alone} the same fields as a whole body.
	 */
	private static <T> List<String> roundTrip(DynamicOps<JsonElement> ops, String name, Codec<T> codec,
			Function<T, Optional<?>> player, String rest, String alone) {
		List<String> problems = new ArrayList<>();
		String bare = "{" + alone + "}";
		DataResult<T> read = codec.parse(ops, JsonParser.parseString(bare));
		if (read.result().isEmpty() || player.apply(read.result().get()).isPresent()) {
			problems.add(name + ": " + bare + " does not read as an instance without a player");
		} else if (!JsonParser.parseString(bare).equals(codec.encodeStart(ops, read.result().get()).result()
				.orElse(null))) {
			problems.add(name + ": " + bare + " does not write back to itself");
		}
		List<String> accepted = new ArrayList<>();
		for (String shape : PLAYER_SHAPES) {
			String json = "{\"player\":" + shape + rest + "}";
			Optional<T> instance = codec.parse(ops, JsonParser.parseString(json)).result();
			if (instance.isEmpty() || player.apply(instance.get()).isEmpty()) {
				continue;
			}
			if (JsonParser.parseString(json).equals(codec.encodeStart(ops, instance.get()).result().orElse(null))) {
				accepted.add(shape);
			}
		}
		if (accepted.isEmpty()) {
			problems.add(name + ": no player shape reads with a player and writes back to itself");
		}
		Industrialization.LOGGER.info("MOD-704 {} codec round-trips the player shapes {}", name, accepted);
		return problems;
	}

	private static void expect(GameTestHelper helper, ServerPlayer player, String advancement, boolean wanted,
			String when) {
		if (earned(helper, player, advancement) != wanted) {
			helper.fail(advancement + " is " + (wanted ? "not " : "") + "earned " + when);
		}
	}
}

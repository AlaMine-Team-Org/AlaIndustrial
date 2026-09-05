package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.WorkstationBlock;
import dev.alaindustrial.block.entity.WorkstationBlockEntity;
import dev.alaindustrial.network.SkillActionPayload;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.skill.PlayerSkills;
import dev.alaindustrial.skill.SkillBranch;
import dev.alaindustrial.skill.SkillBuild;
import dev.alaindustrial.skill.SkillMachine;
import dev.alaindustrial.skill.SkillPoints;
import dev.alaindustrial.skill.SkillSlot;
import dev.alaindustrial.skill.SkillStore;
import dev.alaindustrial.stats.PlayerModStats;
import dev.alaindustrial.stats.PlayerStatsStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Buying a skill at a live station (MOD-483, suite TC-SKILL-BUY-001).
 *
 * <p>The purchase is where the whole system spends things — Ala-Fragments AND the station's energy —
 * and it was the last part with no test of its own. Everything asserted here is a rule the server
 * enforces rather than the screen: the screen greys nodes out, but a packet arrives whatever the
 * screen believed, and these run the handler directly for exactly that reason.
 *
 * <p>The bodies are loader-neutral, but only the two that need no player run on both lanes. The
 * four that hand a mock player a level or a skill are Fabric-only: NeoForge syncs a per-player
 * attachment to its holder the moment it is written, and a vanilla gametest mock has no
 * connection to send it down. The reason is recorded in the parity gate's allow-list rather than
 * left for the next person to rediscover.
 */
public final class SkillPurchaseScenarios {

	private SkillPurchaseScenarios() {
	}

	private static final BlockPos BASE = new BlockPos(1, 2, 1);

	/** An assembled station with a full MV buffer. No player involved. */
	private static WorkstationBlockEntity station(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos base = helper.absolutePos(BASE);
		level.setBlockAndUpdate(base, ModContent.WORKSTATION.get().defaultBlockState());
		level.setBlockAndUpdate(base.above(), ModContent.WORKSTATION.get().defaultBlockState());
		WorkstationBlock.tryAssemble(level, base.above());

		WorkstationBlockEntity be = (WorkstationBlockEntity) level.getBlockEntity(base);
		be.getEnergyStorage().setAmountUntracked(be.getEnergyStorage().getCapacity());
		return be;
	}

	/** The same station, with the player standing on it so the handler's reach check passes. */
	private static WorkstationBlockEntity poweredStation(GameTestHelper helper, ServerPlayer player) {
		WorkstationBlockEntity be = station(helper);
		BlockPos base = helper.absolutePos(BASE);
		// Within reach: the handler refuses a packet naming a station across the world, and a mock
		// player spawned elsewhere would make every assertion below pass for the wrong reason.
		player.setPos(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
		return be;
	}

	/** Top the buffer back up — one charge pays for exactly one lesson. */
	private static void recharge(WorkstationBlockEntity station) {
		station.getEnergyStorage().setAmountUntracked(station.getEnergyStorage().getCapacity());
	}

	/** A player with enough levels to afford anything the tests buy. */
	private static ServerPlayer richPlayer(GameTestHelper helper, int level) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		PlayerStatsStore.set(player, new PlayerModStats(0L, 0L, level, Map.of(), 0L, 0L));
		SkillStore.set(player, PlayerSkills.EMPTY);
		return player;
	}

	private static boolean owns(ServerPlayer player, SkillSlot slot) {
		return SkillStore.build(player).has(SkillBranch.ENERGY, slot);
	}

	/** A full station teaches the node and pays for it out of its own buffer. */
	public static void buyStoresTheNodeAndChargesTheStation(GameTestHelper helper) {
		ServerPlayer player = richPlayer(helper, 10);
		WorkstationBlockEntity station = poweredStation(helper, player);
		long before = station.getEnergyStorage().getAmount();

		SkillActionPayload.handle(
				SkillActionPayload.buy(helper.absolutePos(BASE), SkillBranch.ENERGY, SkillSlot.IN), player);

		helper.assertTrue(owns(player, SkillSlot.IN), "the node must be stored after a paid purchase");
		helper.assertValueEqual(before - station.getEnergyStorage().getAmount(),
				(long) Config.workstationSkillPurchaseEu, "the station pays the configured price");
		helper.succeed();
	}

	/**
	 * An empty station teaches nothing, and — the part that matters — charges nothing.
	 *
	 * <p>The order inside the handler is what this pins down: energy first, skill only if the whole
	 * price was paid. A half-paid purchase would be a skill for free.
	 */
	public static void buyRefusedWhenTheStationIsEmpty(GameTestHelper helper) {
		ServerPlayer player = richPlayer(helper, 10);
		WorkstationBlockEntity station = poweredStation(helper, player);
		station.getEnergyStorage().setAmountUntracked(0L);

		SkillActionPayload.handle(
				SkillActionPayload.buy(helper.absolutePos(BASE), SkillBranch.ENERGY, SkillSlot.IN), player);

		helper.assertFalse(owns(player, SkillSlot.IN), "an unpowered station must teach nothing");
		helper.assertValueEqual(station.getEnergyStorage().getAmount(), 0L,
				"a refused purchase must not leave the buffer in debt");
		helper.succeed();
	}

	/**
	 * A player who has spent every Fragment buys nothing more — and the station keeps its charge.
	 *
	 * <p>Both halves of the budget rule are asserted here. A level-1 player holds <b>nothing</b>: level 1
	 * is where everyone starts, and paying for it used to hand out a free entry node. Level 2 — the first
	 * level anyone earns — is worth exactly one Fragment, which buys the entry and leaves nothing for the
	 * fork above it. Asserting the balance first is what keeps the refusal below from passing for the
	 * wrong reason: a purchase can also be refused for being unreachable, and this test is about neither.
	 */
	public static void buyRefusedWithoutFragments(GameTestHelper helper) {
		ServerPlayer fresh = richPlayer(helper, 1);
		helper.assertValueEqual((long) SkillPoints.earned(PlayerStatsStore.get(fresh)), 0L,
				"level 1 is the starting level and must be worth no Ala-Fragments");

		ServerPlayer player = richPlayer(helper, 2);
		WorkstationBlockEntity station = poweredStation(helper, player);

		helper.assertValueEqual((long) SkillPoints.earned(PlayerStatsStore.get(player)), 1L,
				"the first earned level is one Ala-Fragment");
		SkillActionPayload.handle(
				SkillActionPayload.buy(helper.absolutePos(BASE), SkillBranch.ENERGY, SkillSlot.IN), player);
		helper.assertTrue(owns(player, SkillSlot.IN), "the single Fragment must buy the entry node");

		recharge(station);
		long before = station.getEnergyStorage().getAmount();
		SkillActionPayload.handle(
				SkillActionPayload.buy(helper.absolutePos(BASE), SkillBranch.ENERGY, SkillSlot.A1), player);

		helper.assertFalse(owns(player, SkillSlot.A1),
				"a player out of Fragments must buy nothing, however much energy the station has");
		helper.assertValueEqual(station.getEnergyStorage().getAmount(), before,
				"a rule refusal must not spend the station's energy either");
		helper.succeed();
	}

	/**
	 * The hard fork survives a packet: the closed side is refused however the request arrives.
	 *
	 * <p>The buffer is refilled between purchases on purpose, and it is not test scaffolding for its
	 * own sake — one MV buffer holds 40 000 EU and one lesson costs 30 000, so a station can teach
	 * exactly one skill per charge. Without the refill this test would be asserting that the second
	 * node was refused for want of energy, which is a different rule entirely.
	 */
	public static void buyRefusedOnTheClosedSideOfAFork(GameTestHelper helper) {
		ServerPlayer player = richPlayer(helper, 20);
		WorkstationBlockEntity be = poweredStation(helper, player);
		BlockPos station = helper.absolutePos(BASE);

		SkillActionPayload.handle(SkillActionPayload.buy(station, SkillBranch.ENERGY, SkillSlot.IN), player);
		recharge(be);
		SkillActionPayload.handle(SkillActionPayload.buy(station, SkillBranch.ENERGY, SkillSlot.A1), player);
		helper.assertTrue(owns(player, SkillSlot.A1), "the taken side of the fork must be stored");

		recharge(be);
		SkillActionPayload.handle(SkillActionPayload.buy(station, SkillBranch.ENERGY, SkillSlot.B1), player);
		helper.assertFalse(owns(player, SkillSlot.B1),
				"the other side of a hard fork must stay closed, whatever the client sends");
		helper.succeed();
	}

	/**
	 * A machine whose owner is not in the world runs on base numbers.
	 *
	 * <p>Asserted through {@link SkillMachine} with an owner id nobody is logged in as — which is
	 * exactly the state of every machine on a server whose builder logged off.
	 */
	public static void offlineOwnerGetsNoBuffs(GameTestHelper helper) {
		int base = 200;
		int forGhost = SkillMachine.duration(base, helper.getLevel(), UUID.randomUUID());
		helper.assertValueEqual((long) forGhost, (long) base,
				"an absent owner must not speed anything up");
		helper.assertValueEqual((long) SkillMachine.duration(base, helper.getLevel(), null), (long) base,
				"an ownerless machine must not speed anything up either");
		helper.succeed();
	}

	/**
	 * A bought build survives being written to disk and read back — the save format, round-tripped.
	 *
	 * <p>What this stands in for is the promise "your tree is still there tomorrow". A real relog
	 * cannot be staged inside a gametest (there is no way to stop and restart the world), but the part
	 * that can actually break is the codec: the wire and save form is a flat list of
	 * {@code "branch/SLOT"} strings, and renaming a branch or a slot would silently drop every node a
	 * player had bought. That is a world-breaking class of change, so it gets a test rather than a
	 * promise.
	 *
	 * <p>Also asserts the forward-compatibility rule the decoder is built on: an entry it does not
	 * recognise is skipped, not fatal. Without that, a save written by a newer build with a fifth
	 * branch would refuse to load at all instead of losing one unknown node.
	 */
	public static void buildSurvivesSaveAndLoad(GameTestHelper helper) {
		PlayerSkills original = new PlayerSkills(SkillBuild.EMPTY
				.with(SkillBranch.ENERGY, SkillSlot.IN)
				.with(SkillBranch.ENERGY, SkillSlot.A1)
				.with(SkillBranch.MECH, SkillSlot.IN));

		Tag saved = PlayerSkills.CODEC.encodeStart(NbtOps.INSTANCE, original).getOrThrow();
		PlayerSkills loaded = PlayerSkills.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();

		helper.assertTrue(loaded.build().has(SkillBranch.ENERGY, SkillSlot.IN),
				"the entry node must survive a save and load");
		helper.assertTrue(loaded.build().has(SkillBranch.ENERGY, SkillSlot.A1),
				"the taken side of the fork must survive a save and load");
		helper.assertTrue(loaded.build().has(SkillBranch.MECH, SkillSlot.IN),
				"a node in a second branch must survive too");
		helper.assertValueEqual((long) loaded.build().spent(), (long) original.build().spent(),
				"the number of Ala-Fragments spent must come back unchanged");
		helper.assertFalse(loaded.build().has(SkillBranch.ENERGY, SkillSlot.B1),
				"loading must not invent a node the player never bought");

		// A save written by a build that knows a branch this one does not. The unknown entry is
		// skipped and the known one still loads — otherwise a downgrade would refuse the whole file.
		CompoundTag fromFuture = new CompoundTag();
		ListTag entries = new ListTag();
		entries.add(0, StringTag.valueOf(SkillBranch.ENERGY.key() + "/" + SkillSlot.IN.name()));
		entries.add(1, StringTag.valueOf("no_such_branch/IN"));
		entries.add(2, StringTag.valueOf(SkillBranch.ENERGY.key() + "/NO_SUCH_SLOT"));
		fromFuture.put("taken", entries);

		PlayerSkills tolerant = PlayerSkills.CODEC.parse(NbtOps.INSTANCE, fromFuture).getOrThrow();
		helper.assertTrue(tolerant.build().has(SkillBranch.ENERGY, SkillSlot.IN),
				"a known node must load even when the file also holds entries this build cannot read");
		helper.assertValueEqual((long) tolerant.build().spent(), (long) SkillSlot.IN.cost(),
				"unreadable entries must be skipped, not counted");
		helper.succeed();
	}

	/** Ticks the measurement below runs for. Two seconds is long enough for the old bug to empty the buffer. */
	private static final int UPKEEP_WINDOW_TICKS = 40;

	/** What the station starts the measurement with: more than the correct bill, less than the wrong one. */
	private static final long UPKEEP_PROBE_CHARGE = 2_000L;

	/**
	 * Upkeep is priced per elapsed tick, never per visit to the tick method — the regression test for a
	 * defect the player found in game.
	 *
	 * <p>The station asks to sleep a second between visits and used to debit a second's worth on each
	 * one. But the sleep is only a request: a committed insert calls {@code wake()}, so a station on a
	 * live cable visits every tick, and the bill came to twenty times the configured rate. What that
	 * looked like in the world is the shape of this test: a 64 EU/t supply could never leave anything in
	 * the buffer, so the screens stayed dark, while 128 EU/t — just over the mis-charged 120 — lit them.
	 *
	 * <p>{@code onEachTick} plays the part of the cable: it wakes the block every tick without needing a
	 * generator, so what is measured is the pricing alone.
	 */
	public static void upkeepIsPricedPerTickNotPerVisit(GameTestHelper helper) {
		WorkstationBlockEntity station = station(helper);
		station.getEnergyStorage().setAmountUntracked(UPKEEP_PROBE_CHARGE);

		helper.onEachTick(station::wake);
		helper.runAfterDelay(UPKEEP_WINDOW_TICKS, () -> {
			long spent = UPKEEP_PROBE_CHARGE - station.getEnergyStorage().getAmount();
			// Two ticks of slack: the first visit only starts the clock, and the delay lands a tick either
			// side of the window depending on where in the tick the test was scheduled.
			long ceiling = (long) Config.workstationEuPerTick * (UPKEEP_WINDOW_TICKS + 2);
			helper.assertTrue(spent > 0, "a powered station must spend its upkeep");
			helper.assertTrue(spent <= ceiling,
					"upkeep must not exceed " + Config.workstationEuPerTick + " EU/t: spent " + spent
							+ " EU over " + UPKEEP_WINDOW_TICKS + " ticks, ceiling " + ceiling);
			helper.assertTrue(
					helper.getLevel().getBlockState(helper.absolutePos(BASE)).getValue(WorkstationBlock.LIT),
					"a station with charge left must keep its screens lit");
			helper.succeed();
		});
	}
}

package dev.alaindustrial.gametest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.alaindustrial.block.ReceptionCapsuleBlock;
import dev.alaindustrial.block.entity.ReceptionCapsuleBlockEntity;
import dev.alaindustrial.block.entity.SettlementCoreBlockEntity;
import dev.alaindustrial.command.ShiftWorkerCommand;
import dev.alaindustrial.compat.Invulnerability;
import dev.alaindustrial.entity.ShiftWorker;
import dev.alaindustrial.item.ToolConfig;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.network.ShiftWorkerCardPayload;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModMobs;
import dev.alaindustrial.settlement.SettlementConfig;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.record.RecordNumber;
import dev.alaindustrial.settlement.shift.ShiftMode;
import dev.alaindustrial.settlement.shift.ShiftWorkerBattery;
import dev.alaindustrial.settlement.shift.ShiftWorkerLevels;
import dev.alaindustrial.settlement.shift.ShiftWorkerProfile;
import dev.alaindustrial.settlement.shift.ShiftWorkerProfile.ServiceMark;
import dev.alaindustrial.settlement.shift.ShiftWorkerProfile.Stat;
import dev.alaindustrial.settlement.shift.ShiftWorkerProfiles;
import dev.alaindustrial.settlement.shift.ShiftWorkerRecord;
import dev.alaindustrial.settlement.shift.ShiftWorkers;
import dev.alaindustrial.stats.PlayerStatsStore;
import dev.alaindustrial.stats.PlayerStatsTracker;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.UnaryOperator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The shift worker's profile (MOD-854, epic decisions R67–R70, R73) on both gametest lanes: the battery drains while
 * the worker is awake, the lit core recharges it from its own buffer, a dark core's worker works on its battery and
 * sleeps when it is empty, and wakes when the core lights; only the drain counts towards the level and is credited
 * to the town's owner; points change health and capacity and stop at the cap; the respec costs its price at a
 * capsule; a return takes the last point back; one fight is one wound mark. The old-save case (a full battery at
 * level 1) is {@code shift_worker_save_format_reads_as_written}.
 */
public final class ShiftWorkerProfileScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(ShiftWorkerProfileScenarios::batteryDrainsRechargesSleepsAndWakes,
						"shift_worker_profile_battery_cycle").ticks(240),
				RosterEntry.of(ShiftWorkerProfileScenarios::pointsChangeHealthAndCapacityUpToTheCap,
						"shift_worker_profile_points_and_cap"),
				RosterEntry.of(ShiftWorkerProfileScenarios::respecAtTheCapsuleCostsItsPrice,
						"shift_worker_profile_respec_at_capsule"),
				RosterEntry.of(ShiftWorkerProfileScenarios::aReturnTakesTheLastPointBack,
						"shift_worker_profile_return_takes_last_point"),
				RosterEntry.of(ShiftWorkerProfileScenarios::oneFightIsOneWoundMark,
						"shift_worker_profile_one_fight_one_mark"),
				RosterEntry.of(ShiftWorkerProfileScenarios::aTownsWorkersRememberDifferentThings,
						"shift_worker_profile_biography_in_the_card"),
				RosterEntry.of(ShiftWorkerProfileScenarios::theCardsDressedBodyHasAnId,
						"shift_worker_card_dressed_body_has_an_id"),
				RosterEntry.of(ShiftWorkerProfileScenarios::aBatteryChargesUpToTheFreeCapacity,
						"shift_worker_charge_battery_moves_what_fits"),
				RosterEntry.of(ShiftWorkerProfileScenarios::aFullWorkerTakesNothing,
						"shift_worker_charge_full_worker_takes_nothing"),
				RosterEntry.of(ShiftWorkerProfileScenarios::anythingElseOpensTheCard,
						"shift_worker_charge_other_items_open_the_card"),
				RosterEntry.of(ShiftWorkerProfileScenarios::aClosedRecordTakesNoCharge,
						"shift_worker_charge_closed_record_refuses"),
				RosterEntry.of(ShiftWorkerProfileScenarios::theXpCommandLiftsByTheTable,
						"shift_worker_xp_command_lifts_by_the_table"));

		private Roster() {}
	}

	private ShiftWorkerProfileScenarios() {}

	private static final BlockPos CORE = new BlockPos(1, 2, 1);
	private static final BlockPos CAPSULE = new BlockPos(3, 2, 1);
	private static final BlockPos STAND = new BlockPos(3, 2, 3);

	// ── the battery ─────────────────────────────────────────────────────────────────────────────

	/**
	 * A lit core refills a worker's battery from its buffer on top of its upkeep — exactly the deficit; then dark, the
	 * worker works on its last 3 EU, which carry it over level 2's threshold (a LEVEL mark) and are credited to the
	 * owner as useful work, and it sleeps when they are gone; the lit core wakes it and fills it again.
	 *
	 * @implements MOD-854-BATTERY — drain while awake, recharge by the lit core, sleep when empty, wake when lit
	 * @implements MOD-854-OWNER — the drain is the owner's useful work (R73)
	 */
	public static void batteryDrainsRechargesSleepsAndWakes(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		SettlementCoreBlockEntity core = litCore(helper, owner);
		int town = core.settlementId();
		ShiftWorker worker = spawn(helper, town);
		long capacity = record(helper, worker).profile().capacity();
		edit(helper, worker, profile -> profile.withCharge(capacity - 500));
		long before = core.getEnergyStorage().getAmount();
		AlaGameTestHelper.drive(core, helper, 20);
		long upkeep = 20L * SettlementConfig.settlementCoreEuPerTickLv;
		helper.assertTrue(core.getEnergyStorage().getAmount() == before - upkeep - 500,
				"the core should pay its upkeep " + upkeep + " and the 500 EU recharge: " + before + " -> "
						+ core.getEnergyStorage().getAmount());
		helper.assertTrue(record(helper, worker).profile().chargeNow() == capacity,
				"the recharge did not fill the battery: " + record(helper, worker).profile().chargeNow());

		core.getEnergyStorage().setAmountUntracked(0);
		core.wake();
		AlaGameTestHelper.drive(core, helper, SettlementConfig.settlementCoreGraceTicks);
		helper.assertTrue(!core.isActive(), "control: the core did not go dark");
		edit(helper, worker, profile -> new ShiftWorkerProfile(3, 14_998, 1, profile.investments(),
				profile.issuedGameTime(), profile.marks(), profile.biography()));
		PlayerStatsTracker.get().flush(helper.getLevel().getServer());
		long creditedBefore = PlayerStatsStore.get(owner).euUsefulConsumedTotal();
		helper.startSequence()
				.thenExecuteAfter(5, () -> helper.assertTrue(!worker.isNoAi(),
						"a dark core's worker with charge left must work on its battery (R68)"))
				.thenWaitUntil(() -> helper.assertTrue(worker.isNoAi(),
						"a dark core's worker with an empty battery still runs its goals"))
				.thenExecute(() -> {
					ShiftWorkerProfile profile = record(helper, worker).profile();
					helper.assertTrue(profile.chargeNow() == 0 && profile.euDrained() == 15_001,
							"the last 3 EU should leave the battery: charge " + profile.chargeNow() + ", drained "
									+ profile.euDrained());
					helper.assertTrue(profile.level() == 2 && profile.highestLevel() == 2,
							"15 001 EU drained is level 2, got " + profile.level());
					assertNewest(helper, profile, ServiceMark.Kind.LEVEL, List.of("2"));
					PlayerStatsTracker.get().flush(helper.getLevel().getServer());
					long credited = PlayerStatsStore.get(owner).euUsefulConsumedTotal() - creditedBefore;
					helper.assertTrue(credited == 3, "the owner should be credited the 3 EU drained, got " + credited);
					core.getEnergyStorage().setAmountUntracked(core.getEnergyStorage().getCapacity());
					core.wake();
					AlaGameTestHelper.drive(core, helper, FULL_SWEEP);
					helper.assertTrue(!worker.isNoAi(), "the lit core did not wake its worker");
					helper.assertTrue(record(helper, worker).profile().chargeNow() > 0,
							"the lit core did not start recharging the battery");
					helper.assertTrue(record(helper, worker).profile().euDrained() == 15_001,
							"charging must not count as drained EU");
					finish(helper, worker);
					helper.succeed();
				});
	}

	// ── points ──────────────────────────────────────────────────────────────────────────────────

	/**
	 * Strength adds health and Battery capacity at once; a stat stops at the cap; Capacity takes no point before
	 * stage 5a; a stranger changes nothing.
	 *
	 * @implements MOD-854-POINTS — points change health and capacity; cap 10; inactive stats take none; owner only
	 */
	public static void pointsChangeHealthAndCapacityUpToTheCap(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		int town = registryTown(helper, owner);
		ShiftWorker worker = spawn(helper, town);
		RecordNumber number = record(helper, worker).number();
		edit(helper, worker, profile -> new ShiftWorkerProfile(profile.charge(),
				ShiftWorkerLevels.drainedForLevel(40), 40, List.of(), profile.issuedGameTime(),
				profile.marks(), profile.biography()));
		int cap = SettlementConfig.shiftWorkerStatCap;
		for (int i = 0; i <= cap; i++) {
			ShiftWorkerProfiles.invest(owner, number, Stat.STRENGTH);
		}
		ShiftWorkerProfiles.invest(owner, number, Stat.BATTERY);
		ShiftWorkerProfiles.invest(owner, number, Stat.CAPACITY);
		ShiftWorkerProfile profile = record(helper, worker).profile();
		helper.assertTrue(profile.count(Stat.STRENGTH) == cap, "Strength should stop at " + cap + ", has "
				+ profile.count(Stat.STRENGTH));
		helper.assertTrue(profile.count(Stat.CAPACITY) == 0, "Capacity took a point before stage 5a");
		int health = SettlementConfig.shiftWorkerMaxHealth + cap * SettlementConfig.shiftWorkerHealthPerPoint;
		helper.assertTrue(worker.getMaxHealth() == health, "max health " + worker.getMaxHealth() + ", expected "
				+ health);
		long capacity = SettlementConfig.shiftWorkerBatteryBase + SettlementConfig.shiftWorkerBatteryPerPoint;
		helper.assertTrue(profile.capacity() == capacity && profile.chargeNow() == capacity,
				"one Battery point: capacity " + profile.capacity() + ", charge " + profile.chargeNow());
		helper.assertTrue(profile.freePoints() == 39 - cap - 1, "free points " + profile.freePoints());
		ServerPlayer stranger = AlaGameTestHelper.survivalPlayer(helper);
		stranger.setPos(owner.position());
		if (!stranger.getUUID().equals(owner.getUUID())) {
			ShiftWorkerProfiles.invest(stranger, number, Stat.BATTERY);
			helper.assertTrue(record(helper, worker).profile().count(Stat.BATTERY) == 1,
					"a stranger invested a point (R74: others only view)");
		}
		worker.discard();
		SettlementRegistry.get(helper.getLevel().getServer()).setActive(town, false);
		helper.succeed();
	}

	/**
	 * The respec at a capsule of the worker's town: refused while the capsule holds less than the price, then every
	 * point back and exactly the price taken from the capsule's buffer.
	 *
	 * @implements MOD-854-RESPEC — all points back at the capsule for shiftWorkerRespecCost EU
	 */
	public static void respecAtTheCapsuleCostsItsPrice(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		SettlementCoreBlockEntity core = litCore(helper, owner);
		ReceptionCapsuleBlock.placeWhole(helper.getLevel(), helper.absolutePos(CAPSULE), Direction.NORTH,
				ModContent.RECEPTION_CAPSULE.get());
		ReceptionCapsuleBlockEntity capsule = helper.getBlockEntity(CAPSULE, ReceptionCapsuleBlockEntity.class);
		AlaGameTestHelper.drive(capsule, helper, 1);
		helper.assertTrue(capsule.settlementId() == core.settlementId(), "control: the capsule is not in the town");
		ShiftWorker worker = spawn(helper, core.settlementId());
		RecordNumber number = record(helper, worker).number();
		edit(helper, worker, profile -> new ShiftWorkerProfile(profile.charge(), ShiftWorkerLevels.drainedForLevel(10),
				10, List.of(Stat.STRENGTH, Stat.STRENGTH, Stat.BATTERY), profile.issuedGameTime(),
				profile.marks(), profile.biography()));
		ShiftWorkerProfiles.applyStats(helper.getLevel(), record(helper, worker));
		long price = SettlementConfig.shiftWorkerRespecCost;
		capsule.getEnergyStorage().setAmountUntracked(price - 1);
		ShiftWorkerProfiles.respec(owner, number);
		helper.assertTrue(record(helper, worker).profile().investments().size() == 3,
				"the respec went through with less than its price in the capsule");
		capsule.getEnergyStorage().setAmountUntracked(price + 10_000);
		ShiftWorkerProfiles.respec(owner, number);
		helper.assertTrue(record(helper, worker).profile().investments().isEmpty(), "the respec took no point back");
		helper.assertTrue(capsule.getEnergyStorage().getAmount() == 10_000,
				"the capsule should pay exactly " + price + ", holds " + capsule.getEnergyStorage().getAmount());
		helper.assertTrue(worker.getMaxHealth() == SettlementConfig.shiftWorkerMaxHealth,
				"the Strength points stayed on the body: " + worker.getMaxHealth());
		finish(helper, worker);
		helper.succeed();
	}

	// ── return and marks ────────────────────────────────────────────────────────────────────────

	/**
	 * A dead worker brought back (R39, permadeath off) is one level down with its last invested point taken back,
	 * a full battery and a RETURNED mark; the issue wrote ISSUED and the issuing time.
	 *
	 * @implements MOD-854-RETURN — the return takes the last invested point and a level
	 */
	public static void aReturnTakesTheLastPointBack(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		int town = registryTown(helper, owner);
		ShiftWorker worker = spawn(helper, town);
		ShiftWorkerRecord issued = record(helper, worker);
		helper.assertTrue(issued.profile().issuedGameTime() >= 0 && !issued.profile().marks().isEmpty()
				&& issued.profile().marks().get(0).kind() == ServiceMark.Kind.ISSUED,
				"the issue wrote no ISSUED mark or time: " + issued.profile());
		RecordNumber number = issued.number();
		edit(helper, worker, profile -> new ShiftWorkerProfile(100, ShiftWorkerLevels.drainedForLevel(3), 3,
				List.of(Stat.BATTERY, Stat.STRENGTH), profile.issuedGameTime(), profile.marks(), profile.biography()));
		try (ConfigOverrides overrides = ConfigOverrides.sync()) {
			overrides.set("shiftWorkerPermadeath", false);
			worker.kill(helper.getLevel());
		}
		Optional<ShiftWorker> back = ShiftWorkers.bringBack(helper.getLevel(),
				Vec3.atBottomCenterOf(helper.absolutePos(STAND)), 0.0F, number);
		helper.assertTrue(back.isPresent(), "the dead worker did not come back");
		ShiftWorkerProfile profile = record(helper, back.get()).profile();
		helper.assertTrue(profile.level() == 2 && profile.investments().equals(List.of(Stat.BATTERY)),
				"brought back at level " + profile.level() + " with " + profile.investments());
		helper.assertTrue(profile.chargeNow() == profile.capacity(), "a returned worker has a full battery");
		helper.assertTrue(back.get().getMaxHealth() == SettlementConfig.shiftWorkerMaxHealth,
				"the Strength point taken back still counts: " + back.get().getMaxHealth());
		assertNewest(helper, profile, ServiceMark.Kind.RETURNED, List.of("2"));
		back.get().discard();
		SettlementRegistry.get(helper.getLevel().getServer()).setActive(town, false);
		helper.succeed();
	}

	/**
	 * Two blows close together are one fight and one WOUNDED mark (attacker, health left, maximum); a blow after the
	 * fight window starts another.
	 *
	 * @implements MOD-854-MARKS — a wound is a service mark, one per fight
	 */
	public static void oneFightIsOneWoundMark(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		int town = registryTown(helper, owner);
		ShiftWorker worker = spawn(helper, town);
		int marksBefore = record(helper, worker).profile().marks().size();
		hit(helper, worker, owner);
		hit(helper, worker, owner);
		ShiftWorkerProfile profile = record(helper, worker).profile();
		helper.assertTrue(profile.marks().size() == marksBefore + 1, "two blows of one fight wrote "
				+ (profile.marks().size() - marksBefore) + " marks");
		int left = (int) Math.ceil(worker.getHealth());
		assertNewest(helper, profile, ServiceMark.Kind.WOUNDED, List.of("minecraft:player",
				Integer.toString(left), Integer.toString((int) worker.getMaxHealth())));
		worker.setLastHurtAt(helper.getLevel().getGameTime() - 10_000);
		hit(helper, worker, owner);
		helper.assertTrue(record(helper, worker).profile().marks().size() == marksBefore + 2,
				"a blow after the fight did not start a new mark");
		worker.discard();
		SettlementRegistry.get(helper.getLevel().getServer()).setActive(town, false);
		helper.succeed();
	}

	/**
	 * Two workers of one town are issued with four biography fragments each and different memories (ADR-050 point
	 * 7); the card carries four lines; a worker whose ids were lost (an older save) gets them chosen and kept on its
	 * first card.
	 *
	 * @implements MOD-854-BIOGRAPHY — the biography is chosen at issue, kept, and shown on the card
	 */
	public static void aTownsWorkersRememberDifferentThings(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		int town = registryTown(helper, owner);
		ShiftWorker first = spawn(helper, town);
		ShiftWorker second = spawn(helper, town);
		List<Identifier> a = record(helper, first).profile().biography();
		List<Identifier> b = record(helper, second).profile().biography();
		helper.assertTrue(a.size() == 4 && b.size() == 4, "the issue chose " + a + " and " + b);
		helper.assertTrue(!a.get(3).equals(b.get(3)), "two workers of one town share the memory " + a.get(3));
		ShiftWorkerCardPayload card = ShiftWorkerProfiles.card(owner, record(helper, first), true);
		helper.assertTrue(card.biography().size() == 4, "the card carries " + card.biography().size() + " lines");
		edit(helper, second, profile -> profile.withBiography(List.of()));
		ShiftWorkerProfiles.card(owner, record(helper, second), true);
		List<Identifier> again = record(helper, second).profile().biography();
		helper.assertTrue(again.size() == 4 && !again.get(3).equals(a.get(3)),
				"a worker without kept ids should get four, without the town's memory " + a.get(3) + ": " + again);
		first.discard();
		second.discard();
		SettlementRegistry.get(helper.getLevel().getServer()).setActive(town, false);
		helper.succeed();
	}

	/**
	 * The body the card dresses for a worker its client has not loaded — a closed record, or one out of sight — has
	 * an id before anything draws it: since 26.3 {@code getId()} throws on an entity no level has added, and the
	 * renderer asks for it (the owner's crash of 2026-10-21, opening a closed card). The body stays out of the level.
	 *
	 * @implements MOD-854-CARD-BODY — the card's model of a worker the client has not loaded
	 */
	public static void theCardsDressedBodyHasAnId(GameTestHelper helper) {
		ShiftWorker body = ShiftWorker.displayBody(ModMobs.SHIFT_WORKER.type(), helper.getLevel());
		if (body == null) {
			throw helper.assertionException("the shift worker type made no body");
		}
		int id = body.getId();
		helper.assertTrue(id == ShiftWorker.DISPLAY_ENTITY_ID, "the dressed body has the id " + id);
		helper.assertTrue(helper.getLevel().getEntity(id) == null, "the dressed body must not join the level");
		helper.succeed();
	}

	// ── charging from a battery ─────────────────────────────────────────────────────────────────

	/**
	 * A right click with a charged battery moves {@code min(free capacity, stored)} into the worker (owner's decision
	 * 2026-10-21): 500 EU of room take 500 of a full battery's 2000 and leave it 1500; then a battery smaller than the
	 * room gives all it holds. The charge wakes a hibernating worker and counts as no drain. A stranger may do it,
	 * through the worker's own right click.
	 *
	 * @implements MOD-854-CHARGE — a battery's right click charges the worker up to its free capacity
	 */
	public static void aBatteryChargesUpToTheFreeCapacity(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		int town = registryTown(helper, owner);
		ShiftWorker worker = spawn(helper, town);
		long capacity = record(helper, worker).profile().capacity();
		long drained = record(helper, worker).profile().euDrained();
		edit(helper, worker, profile -> profile.withCharge(capacity - 500));
		worker.setMode(ShiftMode.HIBERNATE);
		worker.setNoAi(true);
		ItemStack battery = battery(ToolConfig.batteryBuffer);
		OptionalLong moved = ShiftWorkerBattery.chargeFrom(helper.getLevel(), worker, battery);
		helper.assertTrue(moved.isPresent() && moved.getAsLong() == 500, "500 EU of room should take 500: " + moved);
		helper.assertTrue(ItemEnergy.get(battery) == ToolConfig.batteryBuffer - 500,
				"the battery should keep what did not fit: " + ItemEnergy.get(battery));
		helper.assertTrue(record(helper, worker).profile().chargeNow() == capacity,
				"the worker should be full: " + record(helper, worker).profile().chargeNow());
		helper.assertTrue(!worker.isNoAi() && worker.mode() != ShiftMode.HIBERNATE,
				"a charge should wake a hibernating worker");

		edit(helper, worker, profile -> profile.withCharge(capacity - 5_000));
		ItemStack small = battery(1_200);
		moved = ShiftWorkerBattery.chargeFrom(helper.getLevel(), worker, small);
		helper.assertTrue(moved.isPresent() && moved.getAsLong() == 1_200, "1200 stored should all go: " + moved);
		helper.assertTrue(ItemEnergy.get(small) == 0, "the battery should be empty: " + ItemEnergy.get(small));
		helper.assertTrue(record(helper, worker).profile().chargeNow() == capacity - 3_800,
				"the worker took the wrong amount: " + record(helper, worker).profile().chargeNow());
		helper.assertTrue(record(helper, worker).profile().euDrained() == drained, "charging must not count as drain");

		ServerPlayer stranger = AlaGameTestHelper.survivalPlayer(helper);
		edit(helper, worker, profile -> profile.withCharge(capacity - 100));
		stranger.setItemInHand(InteractionHand.MAIN_HAND, battery(ToolConfig.batteryBuffer));
		worker.interact(stranger, InteractionHand.MAIN_HAND, worker.position());
		helper.assertTrue(record(helper, worker).profile().chargeNow() == capacity,
				"a stranger's battery click should charge the worker: " + record(helper, worker).profile().chargeNow());
		helper.assertTrue(ItemEnergy.get(stranger.getMainHandItem()) == ToolConfig.batteryBuffer - 100,
				"the stranger's battery should give 100: " + ItemEnergy.get(stranger.getMainHandItem()));
		worker.discard();
		SettlementRegistry.get(helper.getLevel().getServer()).setActive(town, false);
		helper.succeed();
	}

	/**
	 * A full worker takes nothing; the click still counts as a charge (a line on the action bar, no card) and the
	 * battery keeps every EU.
	 *
	 * @implements MOD-854-CHARGE — a full battery takes nothing
	 */
	public static void aFullWorkerTakesNothing(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		int town = registryTown(helper, owner);
		ShiftWorker worker = spawn(helper, town);
		long capacity = record(helper, worker).profile().capacity();
		edit(helper, worker, profile -> profile.withCharge(capacity));
		ItemStack battery = battery(ToolConfig.batteryBuffer);
		OptionalLong moved = ShiftWorkerBattery.chargeFrom(helper.getLevel(), worker, battery);
		helper.assertTrue(moved.isPresent() && moved.getAsLong() == 0, "a full worker should take 0: " + moved);
		helper.assertTrue(ItemEnergy.get(battery) == ToolConfig.batteryBuffer,
				"the battery lost charge into a full worker: " + ItemEnergy.get(battery));
		helper.assertTrue(record(helper, worker).profile().chargeNow() == capacity, "the full worker changed");
		worker.discard();
		SettlementRegistry.get(helper.getLevel().getServer()).setActive(town, false);
		helper.succeed();
	}

	/**
	 * Anything but a charged powered item is no charge, and the click opens the card as before: a stone, an empty
	 * battery, an empty hand. Nothing moves, and neither the worker's record nor the item changes.
	 *
	 * @implements MOD-854-CHARGE — any other item opens the card
	 */
	public static void anythingElseOpensTheCard(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		int town = registryTown(helper, owner);
		ShiftWorker worker = spawn(helper, town);
		long capacity = record(helper, worker).profile().capacity();
		edit(helper, worker, profile -> profile.withCharge(capacity - 500));
		for (ItemStack held : List.of(new ItemStack(Items.STONE), battery(0), ItemStack.EMPTY)) {
			ItemStack before = held.copy();
			OptionalLong moved = ShiftWorkerBattery.chargeFrom(helper.getLevel(), worker, held);
			helper.assertTrue(moved.isEmpty(), held + " should open the card, not charge: " + moved);
			helper.assertTrue(ItemStack.matches(before, held), held + " changed");
		}
		helper.assertTrue(record(helper, worker).profile().chargeNow() == capacity - 500, "the record changed");
		worker.discard();
		SettlementRegistry.get(helper.getLevel().getServer()).setActive(town, false);
		helper.succeed();
	}

	/**
	 * A closed record takes no charge, whether its file is closed for good or waits for a return: the click is no
	 * charge, and the battery keeps every EU.
	 *
	 * @implements MOD-854-CHARGE — a dead or waiting worker refuses the charge
	 */
	public static void aClosedRecordTakesNoCharge(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		int town = registryTown(helper, owner);
		ShiftWorker worker = spawn(helper, town);
		ShiftWorkerRecord alive = record(helper, worker);
		ShiftWorkerRecord.Death death = new ShiftWorkerRecord.Death(Identifier.withDefaultNamespace("generic"),
				Optional.empty(), helper.getLevel().getGameTime());
		for (boolean permadeath : new boolean[] {true, false}) {
			ShiftWorkers.book(helper.getLevel().getServer()).put(alive.withProfile(alive.profile().withCharge(0))
					.died(death, permadeath));
			ItemStack battery = battery(ToolConfig.batteryBuffer);
			OptionalLong moved = ShiftWorkerBattery.chargeFrom(helper.getLevel(), worker, battery);
			helper.assertTrue(moved.isEmpty(), "a closed record (permadeath " + permadeath + ") took a charge");
			helper.assertTrue(ItemEnergy.get(battery) == ToolConfig.batteryBuffer, "the battery lost charge");
		}
		ShiftWorkers.book(helper.getLevel().getServer()).put(alive);
		worker.discard();
		SettlementRegistry.get(helper.getLevel().getServer()).setActive(town, false);
		helper.succeed();
	}

	// ── the test command ────────────────────────────────────────────────────────────────────────

	/**
	 * {@code /ala worker xp <number> <eu>} lifts a level-1 worker to the level the table gives for that drained total,
	 * with that level's free points and its LEVEL and RANK marks; the charge is untouched and the town's owner is
	 * credited nothing. An unknown number fails. Run on a dispatcher of its own: the dev tree the command lives in is
	 * not registered on every server.
	 *
	 * @implements MOD-854-XP-COMMAND — the test command lifts a worker exactly as drained EU would
	 */
	public static void theXpCommandLiftsByTheTable(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		int town = registryTown(helper, owner);
		ShiftWorker worker = spawn(helper, town);
		ShiftWorkerProfile before = record(helper, worker).profile();
		helper.assertTrue(before.level() == 1 && before.euDrained() == 0, "control: a new worker is level 1");
		PlayerStatsTracker.get().flush(helper.getLevel().getServer());
		long creditedBefore = PlayerStatsStore.get(owner).euUsefulConsumedTotal();
		CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
		dispatcher.register(Commands.literal("ala").then(ShiftWorkerCommand.tree()));
		CommandSourceStack source = helper.getLevel().getServer().createCommandSourceStack()
				.withLevel(helper.getLevel()).withSuppressedOutput();
		long eu = ShiftWorkerLevels.drainedForLevel(6);
		int result;
		try {
			result = dispatcher.execute("ala worker xp " + record(helper, worker).number() + " " + eu, source);
		} catch (CommandSyntaxException e) {
			throw helper.assertionException("the command did not parse: " + e.getMessage());
		}
		ShiftWorkerProfile after = record(helper, worker).profile();
		helper.assertTrue(result == 1, "the command reported " + result);
		helper.assertTrue(after.euDrained() == eu && after.level() == 6,
				eu + " EU should be level 6: drained " + after.euDrained() + ", level " + after.level());
		helper.assertTrue(after.freePoints() == ShiftWorkerLevels.pointsForLevel(6),
				"level 6 should give " + ShiftWorkerLevels.pointsForLevel(6) + " free points: " + after.freePoints());
		assertNewest(helper, after, ServiceMark.Kind.RANK,
				List.of(Integer.toString(ShiftWorkerLevels.rankIndex(6) + 1)));
		helper.assertTrue(after.chargeNow() == before.chargeNow(), "the lift must not touch the charge");
		PlayerStatsTracker.get().flush(helper.getLevel().getServer());
		helper.assertTrue(PlayerStatsStore.get(owner).euUsefulConsumedTotal() == creditedBefore,
				"the lift must credit the owner nothing");
		int unknown;
		try {
			unknown = dispatcher.execute("ala worker xp Z-999 1000", source);
		} catch (CommandSyntaxException e) {
			throw helper.assertionException("the command did not parse: " + e.getMessage());
		}
		helper.assertTrue(unknown == 0, "an unknown number should fail, got " + unknown);
		worker.discard();
		SettlementRegistry.get(helper.getLevel().getServer()).setActive(town, false);
		helper.succeed();
	}

	/** One mod battery holding {@code eu}. */
	private static ItemStack battery(long eu) {
		ItemStack stack = new ItemStack(ModContent.BATTERY.get());
		ItemEnergy.set(stack, eu);
		return stack;
	}

	// ── helpers ─────────────────────────────────────────────────────────────────────────────────

	/** Ticks of a lit core that cover one recharge sweep, wherever its countdown stands. */
	private static final int FULL_SWEEP = ShiftWorkerBattery.RECHARGE_INTERVAL + 1;

	private static void assertNewest(GameTestHelper helper, ShiftWorkerProfile profile, ServiceMark.Kind kind,
			List<String> args) {
		List<ServiceMark> marks = profile.marks();
		ServiceMark newest = marks.isEmpty() ? null : marks.get(marks.size() - 1);
		helper.assertTrue(newest != null && newest.kind() == kind && newest.args().equals(args),
				"the newest mark should be " + kind + " " + args + ", the marks are " + marks);
	}

	/** An online survival owner standing next to the worker's spot, the tracker cleared. */
	private static ServerPlayer owner(GameTestHelper helper) {
		PlayerStatsTracker.get().clear();
		ServerPlayer owner = AlaGameTestHelper.survivalPlayer(helper);
		owner.setPos(Vec3.atBottomCenterOf(helper.absolutePos(STAND.east())));
		return owner;
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
			}
		}
	}

	/** A placed core, charged, lit and claimed by {@code owner}. */
	private static SettlementCoreBlockEntity litCore(GameTestHelper helper, ServerPlayer owner) {
		floor(helper);
		SettlementCoreBlockEntity core = AlaGameTestHelper.place(helper, CORE, ModContent.SETTLEMENT_CORE.get(),
				SettlementCoreBlockEntity.class);
		core.getEnergyStorage().setAmountUntracked(core.getEnergyStorage().getCapacity());
		core.wake();
		AlaGameTestHelper.drive(core, helper, 1);
		if (core.claim(owner.getUUID(), "owner") != SettlementRegistry.ClaimResult.CLAIMED) {
			throw helper.assertionException("control: the lit core could not be claimed");
		}
		return core;
	}

	/** A lit town in the registry only — no core block, so nothing recharges — owned by {@code owner}. */
	private static int registryTown(GameTestHelper helper, ServerPlayer owner) {
		floor(helper);
		SettlementRegistry registry = SettlementRegistry.get(helper.getLevel().getServer());
		int town = registry.place(helper.getLevel().dimension(), helper.absolutePos(CORE), 0).settlementId();
		registry.setActive(town, true);
		registry.claim(town, owner.getUUID(), "owner");
		return town;
	}

	private static ShiftWorker spawn(GameTestHelper helper, int town) {
		return ShiftWorkers.spawn(helper.getLevel(), helper.absolutePos(STAND), town)
				.orElseThrow(() -> helper.assertionException("ShiftWorkers.spawn made no worker"));
	}

	private static ShiftWorkerRecord record(GameTestHelper helper, ShiftWorker worker) {
		return ShiftWorkers.book(helper.getLevel().getServer()).byEntity(worker.getUUID())
				.orElseThrow(() -> helper.assertionException("no record for the worker " + worker.getUUID()));
	}

	private static void edit(GameTestHelper helper, ShiftWorker worker, UnaryOperator<ShiftWorkerProfile> change) {
		ShiftWorkerRecord record = record(helper, worker);
		ShiftWorkers.book(helper.getLevel().getServer()).put(record.withProfile(change.apply(record.profile())));
	}

	private static void hit(GameTestHelper helper, ShiftWorker worker, ServerPlayer attacker) {
		Invulnerability.setGraceTicks(worker, 0);
		worker.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(attacker), 2.0F);
	}

	/** The worker leaves and the core is taken away, so the town holds nothing for the scenarios after this one. */
	private static void finish(GameTestHelper helper, ShiftWorker worker) {
		worker.discard();
		helper.setBlock(CORE, Blocks.AIR);
	}
}

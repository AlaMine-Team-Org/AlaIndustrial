package dev.alaindustrial.gametest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.alaindustrial.command.ShiftWorkerCommand;
import dev.alaindustrial.command.TownSuppliesCommand;
import dev.alaindustrial.entity.ShiftWorker;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.drone.DroneNestRules;
import dev.alaindustrial.settlement.drone.RoadWork;
import dev.alaindustrial.settlement.plan.PlanSheetRules;
import dev.alaindustrial.settlement.shift.ShiftWorkerRecord;
import dev.alaindustrial.settlement.shift.ShiftWorkers;
import dev.alaindustrial.settlement.works.TownSupplies;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The two test commands of 0.1.219 on both gametest lanes: {@code /ala town supplies} fills diamond chests next to
 * the player with the town's bill, and {@code /ala worker spawn [force]} restores a worker the capsule's way — refused
 * by the capsule's rules without {@code force}, made with it. Both run on a dispatcher of their own: the dev tree
 * they live in is not registered on every server.
 */
public final class TownTestCommandScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(TownTestCommandScenarios::suppliesFillChestsWithTheBill,
						"town_supplies_command_fills_chests"),
				RosterEntry.of(TownTestCommandScenarios::spawnFollowsTheCapsuleRulesUnlessForced,
						"worker_spawn_command_follows_capsule_rules"));

		private Roster() {
		}
	}

	private static final BlockPos CORE = new BlockPos(1, 2, 1);
	private static final BlockPos STAND = new BlockPos(4, 2, 4);

	private TownTestCommandScenarios() {
	}

	/**
	 * {@code /ala town supplies} in a town that knows no building yet: chests stand on the floor around the player and
	 * hold the plans, the charged drones the tier allows, and at least the estimated road of the four new houses with
	 * the margin.
	 *
	 * @implements MOD-824-R5-SUPPLIES — the supplies command puts the town's bill into chests next to the player
	 */
	public static void suppliesFillChestsWithTheBill(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		int town = town(helper, player);
		int chests = run(helper, player, "ala town supplies");
		helper.assertTrue(chests >= 1, "the command placed no chest: " + chests);
		Set<BlockPos> found = new HashSet<>();
		int plans = 0;
		int drones = 0;
		int roads = 0;
		Set<Item> kinds = new HashSet<>();
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				BlockPos at = helper.absolutePos(new BlockPos(x, 2, z));
				if (!helper.getLevel().getBlockState(at).is(ModContent.DIAMOND_CHEST.get())) {
					continue;
				}
				found.add(at);
				Container chest = (Container) helper.getLevel().getBlockEntity(at);
				for (int slot = 0; slot < chest.getContainerSize(); slot++) {
					ItemStack stack = chest.getItem(slot);
					if (!stack.isEmpty()) {
						kinds.add(stack.getItem());
					}
					plans += stack.is(ModContent.HOUSE_PLAN.get()) ? stack.getCount() : 0;
					drones += stack.is(ModContent.CONSTRUCTION_DRONE.get()) ? stack.getCount() : 0;
					roads += stack.is(item(RoadWork.ROAD_ITEM)) ? stack.getCount() : 0;
				}
			}
		}
		helper.assertTrue(found.size() == chests, "the command reported " + chests + " chests, the floor holds "
				+ found.size());
		// The fixed extras, the road and the earth are ten kinds; the building templates bring the rest.
		helper.assertTrue(kinds.size() > 10, "the templates' blocks are missing: only " + kinds.size() + " kinds");
		// MOD-892: one house plan per type and the warehouse plan, all the same item.
		helper.assertTrue(plans == PlanSheetRules.HOUSE_TYPES + 1, "one house plan per type and the warehouse "
				+ "plan, got " + plans);
		Settlement settlement = SettlementRegistry.get(helper.getLevel().getServer()).find(town).orElseThrow();
		int nests = DroneNestRules.nestLimit(settlement.tier(), true);
		helper.assertTrue(drones == nests, "one drone per nest (" + nests + "), got " + drones);
		int road = TownSupplies.withMargin(PlanSheetRules.HOUSE_TYPES * TownSupplies.ROAD_PER_NEW_HOUSE);
		helper.assertTrue(roads >= road, "at least " + road + " road blocks, got " + roads);
		found.forEach(at -> helper.getLevel().setBlock(at, Blocks.AIR.defaultBlockState(), 3));
		SettlementRegistry.get(helper.getLevel().getServer()).setActive(town, false);
		helper.succeed();
	}

	/**
	 * {@code /ala worker spawn} in a lit, claimed town with no house is refused («no housing»), as the capsule would
	 * refuse it; {@code /ala worker spawn force} makes a worker of that town with a number from the ledger.
	 *
	 * @implements MOD-824-R5-WORKER-SPAWN — the spawn command makes a worker the capsule's way, its rules unless forced
	 */
	public static void spawnFollowsTheCapsuleRulesUnlessForced(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		int town = town(helper, player);
		int before = ShiftWorkers.livingWorkers(helper.getLevel().getServer(), town);
		int refused = run(helper, player, "ala worker spawn");
		helper.assertTrue(refused == 0, "a town with no house should refuse a worker, got " + refused);
		helper.assertTrue(ShiftWorkers.livingWorkers(helper.getLevel().getServer(), town) == before,
				"a refused spawn made a worker");
		Set<UUID> known = new HashSet<>();
		ShiftWorkers.book(helper.getLevel().getServer()).all().forEach(record -> known.add(record.entity()));
		int forced = run(helper, player, "ala worker spawn force");
		helper.assertTrue(forced == 1, "force should make a worker, got " + forced);
		helper.assertTrue(ShiftWorkers.livingWorkers(helper.getLevel().getServer(), town) == before + 1,
				"force should add one living worker to the town");
		ShiftWorkerRecord made = ShiftWorkers.book(helper.getLevel().getServer()).all().stream()
				.filter(record -> !known.contains(record.entity())).findFirst()
				.orElseThrow(() -> helper.assertionException("no new record in the book"));
		helper.assertTrue(made.settlement() == town, "the worker belongs to town " + made.settlement());
		if (helper.getLevel().getEntity(made.entity()) instanceof ShiftWorker worker) {
			worker.discard();
		} else {
			throw helper.assertionException("the new worker is not in the world");
		}
		SettlementRegistry.get(helper.getLevel().getServer()).setActive(town, false);
		helper.succeed();
	}

	// ── helpers ─────────────────────────────────────────────────────────────────────────────────

	private static ServerPlayer player(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
			}
		}
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(STAND)));
		player.setYRot(0.0F);
		return player;
	}

	/** A town in the registry around {@link #CORE}, lit and claimed by {@code owner}, that knows no building. */
	private static int town(GameTestHelper helper, ServerPlayer owner) {
		SettlementRegistry registry = SettlementRegistry.get(helper.getLevel().getServer());
		int town = registry.place(helper.getLevel().dimension(), helper.absolutePos(CORE), 0).settlementId();
		registry.setActive(town, true);
		registry.claim(town, owner.getUUID(), "owner");
		return town;
	}

	/** Runs {@code command} as {@code player} with an operator's rights on the test commands' own dispatcher. */
	private static int run(GameTestHelper helper, ServerPlayer player, String command) {
		CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
		dispatcher.register(Commands.literal("ala").then(ShiftWorkerCommand.tree())
				.then(Commands.literal("town").then(TownSuppliesCommand.tree())));
		CommandSourceStack source = helper.getLevel().getServer().createCommandSourceStack()
				.withLevel(helper.getLevel()).withEntity(player).withPosition(player.position())
				.withSuppressedOutput();
		try {
			return dispatcher.execute(command, source);
		} catch (CommandSyntaxException e) {
			throw helper.assertionException("the command did not parse: " + e.getMessage());
		}
	}

	private static Item item(String id) {
		return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
	}
}

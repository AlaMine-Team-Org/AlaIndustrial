package dev.alaindustrial.gametest;

import dev.alaindustrial.block.ReceptionCapsuleBlock;
import dev.alaindustrial.block.entity.DroneNestBlockEntity;
import dev.alaindustrial.block.entity.ReceptionCapsuleBlockEntity;
import dev.alaindustrial.block.entity.TownDispatcherBlockEntity;
import dev.alaindustrial.item.misc.ConstructionDroneItem;
import dev.alaindustrial.menu.ReceptionCapsuleMenu;
import dev.alaindustrial.menu.SettlementCoreMenu;
import dev.alaindustrial.menu.TownDispatcherMenu;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.settlement.drone.DroneNestRules;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * L2 suite of who may use a town's machines (owner's round 3, point 3; epic MOD-824, decision R98), replayed by both
 * loaders through the roster (ADR-038), on the rig of {@link TownDroneScenarios}: a stranger to a woken town opens
 * neither its core, nor its dispatcher, nor its reception capsule, cannot keep a screen of theirs open (the server
 * answers no click or button of a menu that is not valid), and gets nothing from a drone nest, while the restorer does
 * all of it. The decision is tested below the network replies: a mock player on NeoForge takes no mod payload.
 */
public final class SettlementAccessScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		private Roster() {
		}

		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(SettlementAccessScenarios::strangerIsTurnedAwayFromTheTown,
						"settlement_access_stranger_is_turned_away").ticks(60));
	}

	private SettlementAccessScenarios() {
	}

	/**
	 * @implements MOD-824-R98 — a stranger to a woken town opens none of its machines and keeps no screen of theirs
	 *     open; their right-clicks on a drone nest change nothing; the restorer opens and uses all of them.
	 */
	public static void strangerIsTurnedAwayFromTheTown(GameTestHelper helper) {
		ServerPlayer restorer = AlaGameTestHelper.survivalPlayer(helper);
		ServerPlayer stranger = AlaGameTestHelper.survivalPlayer(helper);
		helper.assertTrue(!restorer.getUUID().equals(stranger.getUUID()), "control: two players with one id");
		TownDroneScenarios.Rig rig = TownDroneScenarios.rig(helper, 1,
				restorer.getUUID(), true);
		ServerLevel level = rig.level();

		// The core: the stranger's screen stays shut, the restorer's opens and the stranger fails its check.
		BlockPos core = rig.core().getBlockPos();
		standBy(core, restorer, stranger);
		helper.assertTrue(!rig.core().admits(stranger) && rig.core().admits(restorer),
				"the core must admit its restorer and turn the stranger away");
		expectRefused(helper, stranger, rig.core(), "settlement core");
		SettlementCoreMenu coreMenu = expectOpened(helper, restorer, rig.core(), SettlementCoreMenu.class,
				"settlement core");
		helper.assertTrue(!coreMenu.stillValid(stranger), "the core's screen must not be valid for a stranger");
		restorer.closeContainer();

		// The dispatcher.
		TownDispatcherBlockEntity dispatcher = rig.dispatcher();
		standBy(dispatcher.getBlockPos(), restorer, stranger);
		expectRefused(helper, stranger, dispatcher, "town dispatcher");
		TownDispatcherMenu dispatcherMenu = expectOpened(helper, restorer, dispatcher, TownDispatcherMenu.class,
				"town dispatcher");
		helper.assertTrue(!dispatcherMenu.stillValid(stranger),
				"the dispatcher's screen must not be valid for a stranger");
		restorer.closeContainer();

		// The reception capsule, beside the hall, inside the town's territory.
		BlockPos capsuleAt = rig.origin().offset(-2, 1, -2);
		for (int up = 0; up < 3; up++) {
			level.setBlock(capsuleAt.above(up), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
		ReceptionCapsuleBlock.placeWhole(level, capsuleAt, Direction.SOUTH, ModContent.RECEPTION_CAPSULE.get());
		if (!(level.getBlockEntity(capsuleAt) instanceof ReceptionCapsuleBlockEntity capsule)) {
			throw helper.assertionException("control: no reception capsule at " + capsuleAt);
		}
		standBy(capsuleAt, restorer, stranger);
		expectRefused(helper, stranger, capsule, "reception capsule");
		ReceptionCapsuleMenu capsuleMenu = expectOpened(helper, restorer, capsule, ReceptionCapsuleMenu.class,
				"reception capsule");
		helper.assertTrue(!capsuleMenu.stillValid(stranger), "the capsule's screen must not be valid for a stranger");
		restorer.closeContainer();

		// The drone nest: the stranger neither takes the drone out nor docks one.
		DroneNestBlockEntity nest = rig.nest(0);
		BlockPos nestAt = nest.getBlockPos();
		BlockState nestState = level.getBlockState(nestAt);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(nestAt), Direction.UP, nestAt, false);
		standBy(nestAt, restorer, stranger);
		stranger.setShiftKeyDown(true);
		nestState.useWithoutItem(level, stranger, hit);
		helper.assertTrue(nest.hasDrone(), "a stranger's sneaking click must not take the drone out");
		restorer.setShiftKeyDown(true);
		nestState.useWithoutItem(level, restorer, hit);
		helper.assertTrue(!nest.hasDrone(), "control: the restorer's sneaking click must take the drone out");
		ItemStack drone = ConstructionDroneItem.withCharge(DroneNestRules.batteryCapacity());
		stranger.setItemInHand(InteractionHand.MAIN_HAND, drone);
		nestState.useItemOn(drone, level, stranger, InteractionHand.MAIN_HAND, hit);
		helper.assertTrue(!nest.hasDrone() && drone.getCount() == 1,
				"a stranger must not dock a drone in the town's nest");
		ItemStack own = ConstructionDroneItem.withCharge(DroneNestRules.batteryCapacity());
		restorer.setItemInHand(InteractionHand.MAIN_HAND, own);
		nestState.useItemOn(own, level, restorer, InteractionHand.MAIN_HAND, hit);
		helper.assertTrue(nest.hasDrone(), "control: the restorer must dock a drone");

		for (int up = 0; up < 3; up++) {
			level.setBlock(capsuleAt.above(up), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
		rig.release();
		helper.succeed();
	}

	/** Puts both players on the cell above {@code pos}, so a menu's reach check passes for both alike. */
	private static void standBy(BlockPos pos, ServerPlayer... players) {
		Vec3 at = Vec3.atBottomCenterOf(pos.above());
		for (ServerPlayer player : players) {
			player.setPos(at.x, at.y, at.z);
		}
	}

	private static void expectRefused(GameTestHelper helper, ServerPlayer player, MenuProvider machine, String what) {
		AbstractContainerMenu before = player.containerMenu;
		player.openMenu(machine);
		helper.assertTrue(player.containerMenu == before, "a stranger must not open the " + what + ", it opened "
				+ player.containerMenu.getClass().getSimpleName());
	}

	private static <M extends AbstractContainerMenu> M expectOpened(GameTestHelper helper, ServerPlayer player,
			MenuProvider machine, Class<M> type, String what) {
		player.openMenu(machine);
		if (!type.isInstance(player.containerMenu)) {
			throw helper.assertionException("control: the restorer must open the " + what + ", got "
					+ player.containerMenu.getClass().getSimpleName());
		}
		M menu = type.cast(player.containerMenu);
		helper.assertTrue(menu.stillValid(player), "control: the " + what + "'s screen must be valid for its restorer");
		return menu;
	}
}

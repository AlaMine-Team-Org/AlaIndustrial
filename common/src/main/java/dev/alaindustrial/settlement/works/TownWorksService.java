package dev.alaindustrial.settlement.works;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.SettlementCoreBlockEntity;
import dev.alaindustrial.menu.SettlementCoreMenu;
import dev.alaindustrial.network.NetworkDispatcher;
import dev.alaindustrial.network.TownWorksPayload;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.drone.TownMaintenance;
import dev.alaindustrial.settlement.shift.ShiftWorkerBook;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The server end of the settlement core's «Construction» tab (MOD-857, epic decisions R88, R95): answers a request
 * with the tab's rows ({@link ConstructionRows}) and carries out the town owner's controls — raise a clearing or a
 * building job on a building, call a job off, put a blocked job back in the queue. A member of the town (or an
 * inspector) near an open core reads the tab, a stranger gets nothing (R98); only the town's restorer changes anything.
 *
 * <p>A request names no core: the server answers for the settlement core menu the player has open, as the shift
 * workers' roster does, and only within {@link #VIEW_DISTANCE} blocks of that core. A request that fails a check
 * gets no reply.
 */
public final class TownWorksService {

	/** How close to the core a player must stand for the tab to answer. */
	public static final double VIEW_DISTANCE = 10.0;
	/** The words of a hall log line about a restored building: «%s restored». */
	public static final String LOG_LINE_KEY = "settlement.alaindustrial.log.building_restored";

	private TownWorksService() {
	}

	/** Answers {@code viewer}: presses {@code action} on {@code target} if they may, then sends the tab's rows. */
	public static void request(ServerPlayer viewer, Optional<ConstructionRow.Action> action, int target) {
		if (!(viewer.containerMenu instanceof SettlementCoreMenu menu) || menu.core() == null) {
			return;
		}
		SettlementCoreBlockEntity core = menu.core();
		BlockPos pos = core.getBlockPos();
		ServerLevel level = viewer.level();
		if (core.isRemoved() || core.getLevel() != level || !level.isLoaded(pos) || level.getBlockEntity(pos) != core
				|| viewer.position().distanceToSqr(Vec3.atCenterOf(pos)) > VIEW_DISTANCE * VIEW_DISTANCE
				|| core.isMisplaced() || core.isUnbound() || !core.admits(viewer)) {
			return; // a stranger to the town reads nothing of it (R98), even with a forged request
		}
		Optional<Settlement> town = core.settlement();
		if (town.isEmpty()) {
			return;
		}
		boolean manage = town.get().isOwnedBy(viewer.getUUID());
		TownWorks works = SettlementRegistry.get(level.getServer()).works().town(town.get().id());
		if (action.isPresent() && manage) {
			press(works, action.get(), target, town.get().id(), viewer);
		}
		send(viewer, new TownWorksPayload(pos, town.get().id(), manage,
				ConstructionRows.collect(new ConstructionRows.Context(level, town.get(), works, manage))));
	}

	/** Carries out one control of the town's owner; a press the rows did not offer does nothing. */
	static void press(TownWorks works, ConstructionRow.Action action, int target, int settlementId,
			ServerPlayer owner) {
		switch (action) {
			case RAISE_CLEAR, RAISE_BUILD -> {
				Optional<TownBuilding> building = works.building(target);
				if (building.isEmpty() || building.get().intact().isEmpty()) {
					return;
				}
				WorkJobType type = action == ConstructionRow.Action.RAISE_CLEAR ? WorkJobType.CLEAR : WorkJobType.BUILD;
				TownMaintenance.raiseByOwner(owner.level(), settlementId, target, type).ifPresent(job ->
						Industrialization.LOGGER.info(
						"[ala-towns] settlement {}: {} raised {} job {} on {}", settlementId,
						owner.getGameProfile().name(), type.serializedName(), job.id(), building.get().template()));
			}
			case CANCEL -> works.queue().cancel(target);
			case START -> works.queue().unblock(target);
			case SCHEMATIC -> {
				// Visual and personal: the client asks for the schematic itself (HallSchematicRequestPayload).
			}
		}
	}

	/**
	 * The words of a hall log line about a restored building — «Town hall restored» — or empty for a line about a
	 * worker. What the town hall's journal will show (MOD-860).
	 */
	public static Optional<Component> logLineText(ShiftWorkerBook.LogLine line) {
		return line.building().map(building -> Component.translatable(LOG_LINE_KEY,
				Component.translatable(building.name().isEmpty() ? ConstructionRows.UNNAMED : building.name())));
	}

	private static void send(ServerPlayer player, TownWorksPayload payload) {
		NetworkDispatcher dispatcher = NetworkDispatcher.get();
		if (dispatcher.canSendToPlayer(player, TownWorksPayload.TYPE)) {
			dispatcher.sendToPlayer(player, payload);
		}
	}
}

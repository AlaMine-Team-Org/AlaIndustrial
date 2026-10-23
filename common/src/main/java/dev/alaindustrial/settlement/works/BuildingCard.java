package dev.alaindustrial.settlement.works;

import dev.alaindustrial.compat.LineStructures;
import dev.alaindustrial.network.BuildingCardPayload;
import dev.alaindustrial.network.BuildingCardRequestPayload;
import dev.alaindustrial.network.NetworkDispatcher;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementGate;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.shift.ShiftWorkerRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/**
 * The card of one building of an ALA town (round 2 of 0.1.219, owner's answer Q2), after MineColonies' hut window: a
 * right click on a house's inlet or on a building's plaque opens it — its name, state and share restored, the key
 * points it still misses, who lives in it, and the one order button its row in the core's «Construction» tab has, with
 * «Show the schematic». The same row ({@link ConstructionRows#buildingRow}) words both, so they never disagree.
 *
 * <p>A member of the town reads it, only the town's restorer presses its order button (R95, R98); a stranger is told
 * why not. Every press is checked again here: the block, the distance, the town and the owner.
 */
public final class BuildingCard {

	/** How close to the block a player must stand for the card to answer. */
	public static final double VIEW_DISTANCE = 10.0;

	private static final String KEY = "settlement.alaindustrial.card.";
	/** «Restored: %s» — the share of the building that matches its intact template. */
	public static final String RESTORED = KEY + "restored";
	/** «Lives here: %s» — the record number of the house's resident. */
	public static final String RESIDENT = KEY + "resident";
	/** «Nobody lives here» — a house without a resident. */
	public static final String NO_RESIDENT = KEY + "no_resident";

	private BuildingCard() {
	}

	/**
	 * {@code player} right-clicked {@code pos}: opens the card of the town building that holds it, and returns whether
	 * one does. A stranger to the town hears why not and gets no card.
	 */
	public static boolean open(ServerPlayer player, BlockPos pos) {
		Optional<Found> found = find(player.level(), pos);
		if (found.isEmpty()) {
			return false;
		}
		if (!SettlementGate.admitOrTell(Optional.of(found.get().town()), player)) {
			return true;
		}
		send(player, card(player, found.get(), pos, true));
		return true;
	}

	/** A press on an open card: an order button, or only a refresh. */
	public static void request(ServerPlayer player, BuildingCardRequestPayload request) {
		BlockPos pos = request.anchor();
		if (player.position().distanceToSqr(Vec3.atCenterOf(pos)) > VIEW_DISTANCE * VIEW_DISTANCE) {
			return;
		}
		Optional<Found> found = find(player.level(), pos);
		if (found.isEmpty() || !SettlementGate.admits(Optional.of(found.get().town()), player)) {
			return;
		}
		Optional<ConstructionRow.Action> action = ConstructionRow.Action.byOrdinal(request.action());
		if (action.isPresent() && (action.get().places() || action.get() == ConstructionRow.Action.ORDER_CANCEL)
				&& found.get().town().isOwnedBy(player.getUUID())) {
			ConstructionRow row = row(player, found.get());
			if (row.allows(action.get())) {
				TownWorksService.press(found.get().town(), found.get().works(), action.get(), found.get().index(),
						player);
			}
		}
		send(player, card(player, found.get(), pos, false));
	}

	/** A town building, its town and its works. */
	private record Found(Settlement town, TownWorks works, int index) {
	}

	/** The building of the town whose territory holds {@code pos} whose box holds it, or whose inlet stands there. */
	private static Optional<Found> find(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return Optional.empty();
		}
		SettlementRegistry registry = SettlementRegistry.get(level.getServer());
		Optional<Settlement> town = registry.territoryAt(level.dimension(), pos);
		if (town.isEmpty()) {
			return Optional.empty();
		}
		TownWorks works = registry.works().town(town.get().id());
		OptionalInt laid = HousePlacement.laidAt(level, town.get().id(), pos);
		if (laid.isPresent()) {
			return Optional.of(new Found(town.get(), works, laid.getAsInt()));
		}
		for (int i = 0; i < works.buildings().size(); i++) {
			TownBuilding building = works.buildings().get(i);
			if (building.withdrawn()) {
				continue;
			}
			Optional<StructureTemplate> template = LineStructures.templates(level).get(building.shapeTemplate());
			if (template.isPresent() && template.get().getBoundingBox(
					new StructurePlaceSettings().setRotation(building.rotation()), building.origin()).isInside(pos)) {
				return Optional.of(new Found(town.get(), works, i));
			}
		}
		return Optional.empty();
	}

	private static ConstructionRow row(ServerPlayer player, Found found) {
		return ConstructionRows.buildingRow(new ConstructionRows.Context(player.level(), found.town(), found.works(),
				found.town().isOwnedBy(player.getUUID())), found.index());
	}

	private static BuildingCardPayload card(ServerPlayer player, Found found, BlockPos anchor, boolean open) {
		ServerLevel level = player.level();
		ConstructionRow row = row(player, found);
		TownBuilding building = found.works().buildings().get(found.index());
		List<ConstructionRow.Text> lines = new ArrayList<>();
		if (building.percent() >= 0) {
			lines.add(ConstructionRow.Text.of(RESTORED, building.percent() + "%"));
		}
		residentLine(level, found.town().id(), found.works(), found.index()).ifPresent(lines::add);
		return new BuildingCardPayload(anchor, found.town().corePos(), open, row, lines);
	}

	/**
	 * Who lives in building {@code index} of town {@code town} — «Lives here: No. 7» or «Nobody lives here» — or empty
	 * for a building that is no house. The core's «Construction» tab shows the same line on the house's card.
	 */
	public static Optional<ConstructionRow.Text> residentLine(ServerLevel level, int town, TownWorks works,
			int index) {
		if (!TownHouses.isHouse(works.buildings().get(index))) {
			return Optional.empty();
		}
		Optional<String> resident = SettlementRegistry.get(level.getServer()).workers().all().stream()
				.filter(record -> record.settlement() == town && record.housed() && record.home() == index
						&& record.status() != ShiftWorkerRecord.Status.DEAD)
				.map(record -> record.number().toString()).findFirst();
		return Optional.of(resident.map(number -> ConstructionRow.Text.of(RESIDENT, number))
				.orElseGet(() -> ConstructionRow.Text.of(NO_RESIDENT)));
	}

	private static void send(ServerPlayer player, BuildingCardPayload payload) {
		NetworkDispatcher dispatcher = NetworkDispatcher.get();
		if (dispatcher.canSendToPlayer(player, BuildingCardPayload.TYPE)) {
			dispatcher.sendToPlayer(player, payload);
		}
	}
}

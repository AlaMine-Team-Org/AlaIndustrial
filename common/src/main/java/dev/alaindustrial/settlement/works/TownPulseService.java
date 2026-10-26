package dev.alaindustrial.settlement.works;

import dev.alaindustrial.block.entity.SettlementCoreBlockEntity;
import dev.alaindustrial.menu.SettlementCoreMenu;
import dev.alaindustrial.network.NetworkDispatcher;
import dev.alaindustrial.network.TownPulsePayload;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.shift.ShiftWorkerBook;
import dev.alaindustrial.settlement.shift.TownChronicle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The server end of the core's town line and «Chronicle» tab (MOD-886): answers a request with what the town needs
 * ({@link TownPulse}) and its chronicle ({@link TownChronicle}). Only the town's own read them (R98): a member near an
 * open lit core, or an inspector; a stranger, or a request that fails a check, gets no reply. A request names no core:
 * the server answers for the settlement core menu the player has open, as the «Construction» tab does.
 */
public final class TownPulseService {

	private TownPulseService() {
	}

	/** Answers {@code viewer} with the needs and the chronicle of the town of the core it has open. */
	public static void request(ServerPlayer viewer) {
		Optional<SettlementCoreBlockEntity> core = openCore(viewer);
		Optional<Settlement> town = core.flatMap(SettlementCoreBlockEntity::settlement);
		if (town.isEmpty()) {
			return;
		}
		send(viewer, reply(viewer.level(), town.get(), core.get()));
	}

	/**
	 * The lit or dark core of a town whose settlement core menu {@code viewer} has open — if the viewer stands close
	 * and is one of the town's own (R98); empty for a stranger, a misplaced core or one of no town, or a request that
	 * fails a check. The town's other requests from the core's screen ask the same (MOD-890).
	 */
	public static Optional<SettlementCoreBlockEntity> openCore(ServerPlayer viewer) {
		if (!(viewer.containerMenu instanceof SettlementCoreMenu menu) || menu.core() == null) {
			return Optional.empty();
		}
		SettlementCoreBlockEntity core = menu.core();
		BlockPos pos = core.getBlockPos();
		ServerLevel level = viewer.level();
		if (core.isRemoved() || core.getLevel() != level || !level.isLoaded(pos) || level.getBlockEntity(pos) != core
				|| viewer.position().distanceToSqr(Vec3.atCenterOf(pos)) > TownWorksService.VIEW_DISTANCE
						* TownWorksService.VIEW_DISTANCE
				|| core.isMisplaced() || core.isUnbound() || !core.admits(viewer)) {
			return Optional.empty(); // a stranger to the town reads nothing of it (R98), even with a forged request
		}
		return Optional.of(core);
	}

	/** The reply for town {@code town} whose core is {@code core}: the pulse only while the core is lit. */
	public static TownPulsePayload reply(ServerLevel level, Settlement town, SettlementCoreBlockEntity core) {
		boolean pulsed = core.isActive();
		List<TownNeeds.Need> needs = List.of();
		if (pulsed) {
			needs = TownPulse.latest(town.id(), level.getGameTime())
					.orElseGet(() -> TownPulse.beat(level, town.id(), core.energyShort())).needs();
		}
		List<ConstructionRow.Text> texts = new ArrayList<>();
		List<String> icons = new ArrayList<>();
		Map<String, String> buildings = buildingIcons(level, town.id());
		String style = NeedIcon.style(buildings.values());
		for (TownNeeds.Need need : needs) {
			texts.add(need.text());
			icons.add(NeedIcon.of(need, buildings, style));
		}
		return new TownPulsePayload(town.id(), pulsed, texts, chronicle(
				SettlementRegistry.get(level.getServer()).workers(), town.id()), icons);
	}

	/** Town {@code town}'s buildings' pictures by the name key its needs name them with (MOD-890). */
	private static Map<String, String> buildingIcons(ServerLevel level, int town) {
		Map<String, String> icons = new LinkedHashMap<>();
		SettlementRegistry.get(level.getServer()).works().find(town).ifPresent(works -> {
			for (TownBuilding building : works.buildings()) {
				icons.putIfAbsent(TownHouses.displayName(building),
						BuildingTile.iconOf(building.intact().orElse(building.template()).getPath()));
			}
		});
		return icons;
	}

	/** Town {@code settlement}'s chronicle as the tab shows it, newest line first. */
	public static List<TownPulsePayload.Line> chronicle(ShiftWorkerBook book, int settlement) {
		List<ShiftWorkerBook.LogLine> log = book.log(settlement);
		List<TownPulsePayload.Line> lines = new ArrayList<>(log.size());
		for (int i = log.size() - 1; i >= 0; i--) {
			ShiftWorkerBook.LogLine line = log.get(i);
			lines.add(new TownPulsePayload.Line(TownChronicle.day(TownChronicle.gameTime(line)),
					TownChronicle.worker(line).orElse(""), TownChronicle.text(line), TownChronicle.repeats(line)));
		}
		return lines;
	}

	private static void send(ServerPlayer player, TownPulsePayload payload) {
		NetworkDispatcher dispatcher = NetworkDispatcher.get();
		if (dispatcher.canSendToPlayer(player, TownPulsePayload.TYPE)) {
			dispatcher.sendToPlayer(player, payload);
		}
	}
}

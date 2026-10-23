package dev.alaindustrial.settlement.works;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.chat.ModChat;
import dev.alaindustrial.compat.LineStructures;
import dev.alaindustrial.item.misc.RestorationPlan;
import dev.alaindustrial.network.HallSchematicPayload;
import dev.alaindustrial.network.HousePlacementPayload;
import dev.alaindustrial.network.HousePlacementRequestPayload;
import dev.alaindustrial.network.NetworkDispatcher;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.plan.PlanSheetRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.phys.Vec3;

/**
 * The server end of the house laying window (MOD-877, R108): opens it for the town's restorer who puts a house inlet
 * down on the town's territory or right-clicks it, answers its presses, and sends what it shows — the plans the town
 * knows, the site's problems ({@link HousePlacement#survey}) and the house as ghosts. A press is checked again here:
 * the inlet, the town, the restorer (R95) and the distance; a stranger's or a forged request gets no reply.
 */
public final class HousePlacementWindow {

	private HousePlacementWindow() {
	}

	/**
	 * The inlet at {@code inlet} was put down ({@code placed}), or right-clicked, by {@code player}: opens the window
	 * if it may, and returns whether it did. Only a placement says why not.
	 */
	public static boolean open(ServerPlayer player, BlockPos inlet, boolean placed) {
		Optional<Settlement> town = townFor(player, inlet, placed);
		if (town.isEmpty()) {
			return false;
		}
		send(player, view(player, town.get(), inlet, 0, -1, true));
		return true;
	}

	/** A press in the window: preview another plan or turn, lay the house, or call it off. */
	public static void request(ServerPlayer player, HousePlacementRequestPayload request) {
		BlockPos inlet = request.inlet();
		double reach = HousePlacement.VIEW_DISTANCE;
		if (player.position().distanceToSqr(Vec3.atCenterOf(inlet)) > reach * reach) {
			return;
		}
		Optional<Settlement> town = townFor(player, inlet, false);
		if (town.isEmpty()) {
			return;
		}
		ServerLevel level = player.level();
		if (request.action() == HousePlacementRequestPayload.LAY) {
			List<RestorationPlan> plans = HousePlacement.knownPlans(level, town.get(), player);
			if (!plans.isEmpty()) {
				RestorationPlan plan = plans.get(Math.floorMod(request.plan(), plans.size()));
				HousePlacement.lay(level, town.get(), inlet, plan, rotation(request.rotation()));
			}
		} else if (request.action() == HousePlacementRequestPayload.CANCEL) {
			TownWorks works = SettlementRegistry.get(level.getServer()).works().town(town.get().id());
			OptionalInt laid = HousePlacement.laidAt(level, town.get().id(), inlet);
			if (laid.isPresent() && HousePlacement.cancellable(works, laid.getAsInt())) {
				works.withdraw(laid.getAsInt());
				Industrialization.LOGGER.info("[ala-towns] settlement {}: {} called off laid house {}",
						town.get().id(), player.getGameProfile().name(), laid.getAsInt());
			}
		}
		send(player, view(player, town.get(), inlet, request.plan(), request.rotation(), false));
	}

	/**
	 * The town whose territory holds the inlet at {@code inlet}, when {@code player} is its restorer and the inlet is
	 * no fitting of a building the town already has. {@code tell}: say in the action bar why not.
	 */
	private static Optional<Settlement> townFor(ServerPlayer player, BlockPos inlet, boolean tell) {
		ServerLevel level = player.level();
		if (!level.isLoaded(inlet) || !level.getBlockState(inlet).is(ModContent.HOUSE_INLET.get())) {
			return Optional.empty();
		}
		SettlementRegistry registry = SettlementRegistry.get(level.getServer());
		Optional<Settlement> town = registry.territoryAt(level.dimension(), inlet);
		if (town.isEmpty()) {
			if (tell) {
				player.sendOverlayMessage(Component.translatable(HousePlacement.NOT_HERE));
			}
			return Optional.empty();
		}
		if (HousePlacement.laidAt(level, town.get().id(), inlet).isEmpty()
				&& partOfBuilding(level, town.get().id(), inlet)) {
			return Optional.empty(); // the inlet of a house being restored by hand, not a new house
		}
		if (!town.get().isOwnedBy(player.getUUID())) {
			if (tell) {
				player.sendSystemMessage(ModChat.line(Component.translatable(HousePlacement.NOT_RESTORER)), false);
			}
			return Optional.empty();
		}
		return town;
	}

	/** Whether {@code pos} lies in the box of one of the town's buildings (a ruin, or a house laid elsewhere). */
	private static boolean partOfBuilding(ServerLevel level, int town, BlockPos pos) {
		TownWorks works = SettlementRegistry.get(level.getServer()).works().town(town);
		StructureTemplateManager templates = LineStructures.templates(level);
		for (TownBuilding building : works.buildings()) {
			if (building.withdrawn()) {
				continue;
			}
			Optional<StructureTemplate> template = templates.get(building.shapeTemplate());
			if (template.isPresent() && template.get().getBoundingBox(
					new StructurePlaceSettings().setRotation(building.rotation()), building.origin()).isInside(pos)) {
				return true;
			}
		}
		return false;
	}

	/** The window's contents for the inlet at {@code inlet}: a laid house's state, or the survey of the choice. */
	private static HousePlacementPayload view(ServerPlayer player, Settlement town, BlockPos inlet, int planIndex,
			int rotationIndex, boolean open) {
		ServerLevel level = player.level();
		TownWorks works = SettlementRegistry.get(level.getServer()).works().town(town.id());
		OptionalInt laid = HousePlacement.laidAt(level, town.id(), inlet);
		if (laid.isPresent()) {
			TownBuilding house = works.buildings().get(laid.getAsInt());
			int type = house.intact().map(id -> PlanSheetRules.houseType(lastSegment(id))).orElse(0);
			boolean cancellable = HousePlacement.cancellable(works, laid.getAsInt());
			return new HousePlacementPayload(inlet, town.corePos(), open, true, cancellable,
					List.of(type), 0, house.rotation().ordinal(), List.of(), List.of(), -1);
		}
		List<RestorationPlan> plans = HousePlacement.knownPlans(level, town, player);
		if (plans.isEmpty()) {
			return new HousePlacementPayload(inlet, town.corePos(), open, false, false, List.of(), 0, 0,
					List.of(ConstructionRow.Text.of(HousePlacement.NO_PLANS)), List.of(), -1);
		}
		int selected = Math.floorMod(planIndex, plans.size());
		RestorationPlan plan = plans.get(selected);
		Rotation rotation = rotationIndex < 0 ? HousePlacement.facingRotation(level, plan, level.getBlockState(inlet))
				: rotation(rotationIndex);
		List<Integer> types = plans.stream().map(p -> PlanSheetRules.houseType(p.building())).toList();
		Optional<HousePlacement.Site> site = HousePlacement.survey(level, town, inlet, plan, rotation);
		if (site.isEmpty()) {
			return new HousePlacementPayload(inlet, town.corePos(), open, false, false, types, selected,
					rotation.ordinal(), List.of(ConstructionRow.Text.of(HousePlacement.NO_PLANS)), List.of(), -1);
		}
		return new HousePlacementPayload(inlet, town.corePos(), open, false, false, types, selected,
				rotation.ordinal(), site.get().problems(), ghosts(site.get().plan(), inlet, site.get().branch()),
				site.get().branch().map(List::size).orElse(HousePlacementPayload.NO_WAY));
	}

	/** The house's blocks and the road it takes as ghosts, the inlet the player put down left out. */
	private static List<HallSchematicPayload.Ghost> ghosts(BuildingPlan plan, BlockPos inlet,
			Optional<List<BlockPos>> branch) {
		List<HallSchematicPayload.Ghost> out = new ArrayList<>();
		// The branch to the town's road first, so the cap never cuts it (round 2 of 0.1.219, step 8).
		branch.ifPresent(cells -> cells.forEach(pos -> out.add(new HallSchematicPayload.Ghost(pos.asLong(),
				ModContent.TOWN_ROAD.get().defaultBlockState()))));
		for (int i = 0; i < plan.size() && out.size() < HallSchematicPayload.MAX_CELLS; i++) {
			if (!plan.expected(i).isAir() && !plan.pos(i).equals(inlet)) {
				out.add(new HallSchematicPayload.Ghost(plan.packedPos(i), plan.expected(i)));
			}
		}
		return out;
	}

	private static Rotation rotation(int index) {
		Rotation[] all = Rotation.values();
		return all[Math.floorMod(index, all.length)];
	}

	private static String lastSegment(Identifier id) {
		String path = id.getPath();
		return path.substring(path.lastIndexOf('/') + 1);
	}

	private static void send(ServerPlayer player, HousePlacementPayload payload) {
		NetworkDispatcher dispatcher = NetworkDispatcher.get();
		if (dispatcher.canSendToPlayer(player, HousePlacementPayload.TYPE)) {
			dispatcher.sendToPlayer(player, payload);
		}
	}
}

package dev.alaindustrial.settlement.drone;

import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.works.BuildingPlan;
import dev.alaindustrial.settlement.works.TownBuilding;
import dev.alaindustrial.settlement.works.TownWorks;
import dev.alaindustrial.settlement.works.TownWorksScanner;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * A town's town hall as the dispatcher and the drone nests need it (MOD-858, epic decision R91): which building of the
 * town it is, and whether a block stands inside it. «Inside» is the box around the cells of the hall's plan
 * ({@link BuildingPlan#contains}) — the intact hall's footprint and height, its roof with the drone pad included.
 */
public final class TownHall {

	/** The last segment of a town hall's template path, in every style. */
	public static final String TEMPLATE_NAME = "town_hall";

	private TownHall() {
	}

	/** The index of the town hall among {@code works}' buildings, if the town knows one. */
	public static OptionalInt index(TownWorks works) {
		for (int i = 0; i < works.buildings().size(); i++) {
			TownBuilding building = works.buildings().get(i);
			String path = building.template().getPath();
			if (path.substring(path.lastIndexOf('/') + 1).equals(TEMPLATE_NAME)) {
				return OptionalInt.of(i);
			}
		}
		return OptionalInt.empty();
	}

	/** The plan of town {@code town}'s hall, if the town knows its hall and the hall has an intact template. */
	public static Optional<BuildingPlan> plan(ServerLevel level, int town) {
		Optional<TownWorks> works = SettlementRegistry.get(level.getServer()).works().find(town);
		if (works.isEmpty()) {
			return Optional.empty();
		}
		OptionalInt index = index(works.get());
		return index.isEmpty() ? Optional.empty() : TownWorksScanner.plan(level, town, index.getAsInt());
	}

	/**
	 * Whether town {@code town}'s hall has been restored (R91: the finished drone pad takes one nest more). Once is
	 * enough: a hall in repair since keeps its pad.
	 */
	public static boolean restored(ServerLevel level, int town) {
		Optional<TownWorks> works = SettlementRegistry.get(level.getServer()).works().find(town);
		if (works.isEmpty()) {
			return false;
		}
		OptionalInt index = index(works.get());
		return index.isPresent() && works.get().buildings().get(index.getAsInt()).everRestored();
	}

	/** How many blocks over the hall's highest layer a drone nest may still stand. */
	public static final int NEST_ABOVE_ROOF = 8;

	/**
	 * Whether a drone nest at {@code pos} stands over town {@code town}'s hall (owner's round 1): anywhere over the
	 * hall's footprint, from its floor to {@link #NEST_ABOVE_ROOF} blocks over its roof. The yellow carpets of the
	 * drone pad are only a suggestion.
	 */
	public static boolean over(ServerLevel level, int town, BlockPos pos) {
		return plan(level, town).map(plan -> plan.over(pos, NEST_ABOVE_ROOF)).orElse(false);
	}

	/** Whether {@code pos} stands inside town {@code town}'s hall. */
	public static boolean contains(ServerLevel level, int town, BlockPos pos) {
		return plan(level, town).map(plan -> plan.contains(pos)).orElse(false);
	}
}

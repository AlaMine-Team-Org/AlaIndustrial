package dev.alaindustrial.settlement;

import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.core.energy.CableNode;
import dev.alaindustrial.core.energy.EnergyNetwork;
import dev.alaindustrial.core.energy.NetworkManager;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.settlement.drone.TownHall;
import dev.alaindustrial.settlement.works.BuildingPlan;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Whether a town building is wired to its town (MOD-861; epic MOD-824, decisions R111, R112): its house inlet
 * shares one energy network with the town's core — through a road, a player's cable, or both.
 *
 * <p><b>The town's side of the line.</b> The core itself, and the town hall's own inlet (R106): the hall's inlet is
 * the port of the core, the place the town's roads start (R114), so a line that reaches it reaches the core. A town
 * hall needs no link: its core stands inside it.
 *
 * <p><b>Which network.</b> The network of a cable segment — a road or any cable — that touches the inlet's outer face,
 * and of every segment touching the core or the hall inlet's outer face. One network object on both sides is one
 * line. A segment that has not joined its network yet (a chunk just loaded: cables register on their first tick)
 * makes the answer {@link Link#UNKNOWN}, never a false «cut».
 */
public final class TownPowerLinks {

	/** The key point a building misses while its inlet is not wired to the core (R112). */
	public static final String NOT_CONNECTED_KEY = "settlement.alaindustrial.building.key.core_link";

	/** The answer for one inlet. */
	public enum Link {
		/** The inlet shares a network with the core. */
		CONNECTED,
		/** It does not. */
		CUT,
		/** A segment on the way has not joined its network yet: ask again later. */
		UNKNOWN
	}

	/** A house inlet in the world: where it stands and which way its socket looks. */
	public record Socket(BlockPos pos, Direction outward) {
		public Socket {
			pos = pos.immutable();
		}

		/** The cell in front of the socket, where a road or a cable joins it. */
		public BlockPos front() {
			return pos.relative(outward);
		}
	}

	private TownPowerLinks() {
	}

	/** The house inlet the intact building of {@code plan} has, if it has one. */
	public static Optional<Socket> inletOf(BuildingPlan plan) {
		for (int i = 0; i < plan.size(); i++) {
			BlockState want = plan.expected(i);
			if (want.is(ModContent.HOUSE_INLET.get()) && want.hasProperty(HorizontalMachineBlock.FACING)) {
				return Optional.of(new Socket(plan.pos(i), want.getValue(HorizontalMachineBlock.FACING)));
			}
		}
		return Optional.empty();
	}

	/** The inlet of town {@code town}'s hall (R106), if the hall's intact template has one. */
	public static Optional<Socket> hallInlet(ServerLevel level, int town) {
		return TownHall.plan(level, town).flatMap(TownPowerLinks::inletOf);
	}

	/**
	 * Whether the house inlet {@code inlet} of town {@code town} is wired to the town's core. A town with no core in
	 * place reads {@link Link#CUT}.
	 */
	public static Link link(ServerLevel level, int town, Socket inlet) {
		Optional<Settlement> settlement = SettlementRegistry.get(level.getServer()).find(town);
		if (settlement.isEmpty() || !settlement.get().corePlaced()) {
			return Link.CUT;
		}
		return link(level, inlet, settlement.get().corePos(), hallInlet(level, town));
	}

	/** {@link #link(ServerLevel, int, Socket)} with the core and the hall's inlet given: what an L2 test asks. */
	public static Link link(ServerLevel level, Socket inlet, BlockPos core, Optional<Socket> hall) {
		BlockPos front = inlet.front();
		if (front.equals(core) || hall.map(h -> h.pos().equals(front)).orElse(false)) {
			return Link.CONNECTED; // flush against the core or the hall's inlet: no cable to ask
		}
		if (!level.isLoaded(front) || !(level.getBlockEntity(front) instanceof CableNode)) {
			return Link.CUT;
		}
		EnergyNetwork network = NetworkManager.networkAt(level, front);
		if (network == null) {
			return Link.UNKNOWN;
		}
		Set<EnergyNetwork> town = Collections.newSetFromMap(new IdentityHashMap<>());
		boolean unknown = false;
		for (Direction face : Direction.values()) {
			unknown |= collect(level, core.relative(face), town);
		}
		if (hall.isPresent()) {
			unknown |= collect(level, hall.get().front(), town);
		}
		if (town.contains(network)) {
			return Link.CONNECTED;
		}
		return unknown ? Link.UNKNOWN : Link.CUT;
	}

	/** Adds the network of the segment at {@code pos} to {@code into}; true when a segment there has none yet. */
	private static boolean collect(ServerLevel level, BlockPos pos, Set<EnergyNetwork> into) {
		if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof CableNode)) {
			return false;
		}
		EnergyNetwork network = NetworkManager.networkAt(level, pos);
		if (network == null) {
			return true;
		}
		into.add(network);
		return false;
	}

	/**
	 * The missing key point of a measured building (R112): {@link #NOT_CONNECTED_KEY} when its intact template has an
	 * inlet, the inlet stands in the world and is cut from the core. Empty for the town hall (its core is inside), for
	 * a building without an inlet, an inlet not built yet (that is a key point of its own), or an unknown link.
	 */
	public static Optional<String> missingLink(ServerLevel level, int town, boolean hall, BuildingPlan plan) {
		if (hall) {
			return Optional.empty();
		}
		Optional<Socket> inlet = inletOf(plan);
		if (inlet.isEmpty() || !level.isLoaded(inlet.get().pos())
				|| !level.getBlockState(inlet.get().pos()).is(ModContent.HOUSE_INLET.get())) {
			return Optional.empty();
		}
		return link(level, town, inlet.get()) == Link.CUT ? Optional.of(NOT_CONNECTED_KEY) : Optional.empty();
	}

	/**
	 * The core of the town whose hall the house inlet at {@code pos} stands in (R106), or null: such an inlet is the
	 * port of its core. Reads the registry and the hall's cached plan, no chunk.
	 */
	public static @Nullable BlockPos hallCoreOf(ServerLevel level, BlockPos pos) {
		Optional<Settlement> town = SettlementRegistry.get(level.getServer()).territoryAt(level.dimension(), pos);
		if (town.isEmpty() || !town.get().corePlaced()) {
			return null;
		}
		Optional<Socket> hall = hallInlet(level, town.get().id());
		return hall.isPresent() && hall.get().pos().equals(pos) ? town.get().corePos() : null;
	}
}

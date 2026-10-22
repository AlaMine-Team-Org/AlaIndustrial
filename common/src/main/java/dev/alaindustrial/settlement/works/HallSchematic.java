package dev.alaindustrial.settlement.works;

import dev.alaindustrial.block.entity.SettlementCoreBlockEntity;
import dev.alaindustrial.network.HallSchematicPayload;
import dev.alaindustrial.network.NetworkDispatcher;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.drone.TownHall;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The server end of the town hall's schematic (MOD-857, owner's round 2, point 7): the cells of the hall that do not
 * match its intact template yet, each with the block that belongs there, for the player who turned the schematic on
 * in the core's «Construction» tab. The client draws them as ghost blocks; the server keeps nothing about who looks.
 *
 * <p><b>Which cells.</b> Every cell of the hall's plan ({@link BuildingPlan}) that wants a block and does not hold it,
 * in a loaded chunk. Debris (a cell the intact hall keeps empty) is not drawn: a ghost of air shows nothing. A cell on
 * the shaft of the lab under the hall is left out too: the builder drone never fills it (R93), and the schematic shows
 * what the drone will build. More than {@link HallSchematicPayload#MAX_CELLS}: the nearest to the player
 * ({@link SchematicSelection}).
 *
 * <p>Visual only, so any member of the town near the core may ask, restorer or not; a stranger to the town gets no
 * reply (R98). The plan is the cached one
 * ({@link TownWorksScanner#plan}); a reply reads at most one block state per plan cell.
 */
public final class HallSchematic {

	/** How far from the core, along the farther horizontal axis, a request is still answered. */
	public static final double VIEW_DISTANCE = 192.0;

	private HallSchematic() {
	}

	/** Answers {@code player}'s request for the schematic of the town whose core stands at {@code corePos}. */
	public static void request(ServerPlayer player, BlockPos corePos) {
		ServerLevel level = player.level();
		if (!level.isLoaded(corePos)
				|| !(level.getBlockEntity(corePos) instanceof SettlementCoreBlockEntity core)
				|| core.isMisplaced() || core.isUnbound() || !core.admits(player)
				|| Math.max(Math.abs(player.getX() - (corePos.getX() + 0.5)),
						Math.abs(player.getZ() - (corePos.getZ() + 0.5))) > VIEW_DISTANCE) {
			return;
		}
		Optional<Settlement> town = core.settlement();
		if (town.isEmpty()) {
			return;
		}
		HallSchematicPayload reply = reply(level, corePos, town.get().id(), player.position());
		NetworkDispatcher dispatcher = NetworkDispatcher.get();
		if (dispatcher.canSendToPlayer(player, HallSchematicPayload.TYPE)) {
			dispatcher.sendToPlayer(player, reply);
		}
	}

	/** The reply for town {@code town}'s hall as seen from {@code viewer}; public for the L2 suite. */
	public static HallSchematicPayload reply(ServerLevel level, BlockPos corePos, int town, Vec3 viewer) {
		Optional<BuildingPlan> plan = TownHall.plan(level, town);
		if (plan.isEmpty()) {
			return new HallSchematicPayload(level.dimension(), corePos, false, 0, List.of());
		}
		List<HallSchematicPayload.Ghost> missing = missing(level, plan.get());
		int[] x = new int[missing.size()];
		int[] y = new int[missing.size()];
		int[] z = new int[missing.size()];
		for (int i = 0; i < missing.size(); i++) {
			long pos = missing.get(i).pos();
			x[i] = BlockPos.getX(pos);
			y[i] = BlockPos.getY(pos);
			z[i] = BlockPos.getZ(pos);
		}
		List<HallSchematicPayload.Ghost> sent = new ArrayList<>();
		for (int index : SchematicSelection.pick(x, y, z, viewer.x, viewer.y, viewer.z,
				HallSchematicPayload.MAX_CELLS)) {
			sent.add(missing.get(index));
		}
		return new HallSchematicPayload(level.dimension(), corePos, true, missing.size(), sent);
	}

	/** Every cell of {@code plan} still to build, in the plan's order. */
	static List<HallSchematicPayload.Ghost> missing(ServerLevel level, BuildingPlan plan) {
		List<HallSchematicPayload.Ghost> out = new ArrayList<>();
		for (int i = 0; i < plan.size(); i++) {
			BlockState want = plan.expected(i);
			if (want.isAir() || plan.onLabShaft(i)) {
				continue;
			}
			BlockPos pos = plan.pos(i);
			if (!level.isLoaded(pos) || plan.matches(i, level.getBlockState(pos))) {
				continue;
			}
			out.add(new HallSchematicPayload.Ghost(plan.packedPos(i), want));
		}
		return out;
	}
}

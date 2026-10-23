package dev.alaindustrial.network;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.settlement.works.ConstructionRow;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C: the house laying window of one house inlet (MOD-877, R108) — sent to the town's restorer who put the inlet
 * down or right-clicked it, and as the reply to every {@link HousePlacementRequestPayload}. The client knows no plan
 * or rule: it shows the plans by their house type, the problems the core named, and the house as ghosts.
 *
 * @param inlet       the inlet the window is about
 * @param core        the town's settlement core: the ghosts are drawn from its block, as the hall's schematic is
 * @param open        open the window (an inlet put down or right-clicked), not only update an open one
 * @param laid        a house is laid from this inlet already
 * @param cancellable the laid house may still be called off: its building has not started
 * @param types       the house types of the plans the town knows, in the order the server picks them by
 * @param selected    the plan shown, an index into {@code types}
 * @param rotation    the house's turn, a {@code Rotation} ordinal
 * @param problems    everything in the house's way, one sentence each; empty — it may be laid
 * @param ghosts      the house's blocks where they would stand
 * @param branch      the road cells the house takes to join the town's network (round 2 of 0.1.219, step 8), -1 for
 *                    nothing to show (a house laid already), or {@link #NO_WAY} when no way reaches the network
 */
public record HousePlacementPayload(BlockPos inlet, BlockPos core, boolean open, boolean laid, boolean cancellable,
		List<Integer> types, int selected, int rotation, List<ConstructionRow.Text> problems,
		List<HallSchematicPayload.Ghost> ghosts, int branch) implements CustomPacketPayload {

	public static final Type<HousePlacementPayload> TYPE = new Type<>(Industrialization.id("house_placement"));

	/** {@link #branch} of a site no way joins to the town's network. */
	public static final int NO_WAY = -2;

	/** Most plans and problems one reply carries. */
	public static final int MAX_LINES = 32;

	public HousePlacementPayload {
		inlet = inlet.immutable();
		core = core.immutable();
		types = List.copyOf(types.subList(0, Math.min(MAX_LINES, types.size())));
		problems = List.copyOf(problems.subList(0, Math.min(MAX_LINES, problems.size())));
		ghosts = List.copyOf(ghosts.subList(0, Math.min(HallSchematicPayload.MAX_CELLS, ghosts.size())));
	}

	public static final StreamCodec<RegistryFriendlyByteBuf, HousePlacementPayload> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, HousePlacementPayload::inlet,
			BlockPos.STREAM_CODEC, HousePlacementPayload::core,
			ByteBufCodecs.BOOL, HousePlacementPayload::open,
			ByteBufCodecs.BOOL, HousePlacementPayload::laid,
			ByteBufCodecs.BOOL, HousePlacementPayload::cancellable,
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MAX_LINES)), HousePlacementPayload::types,
			ByteBufCodecs.VAR_INT, HousePlacementPayload::selected,
			ByteBufCodecs.VAR_INT, HousePlacementPayload::rotation,
			TownWorksPayload.TEXT.apply(ByteBufCodecs.list(MAX_LINES)), HousePlacementPayload::problems,
			HallSchematicPayload.GHOST.apply(ByteBufCodecs.list(HallSchematicPayload.MAX_CELLS)),
			HousePlacementPayload::ghosts,
			ByteBufCodecs.VAR_INT, HousePlacementPayload::branch,
			HousePlacementPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

package dev.alaindustrial.network;

import dev.alaindustrial.Industrialization;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * S2C: the cells of a town hall still to build, for the schematic one player turned on (MOD-857, owner's round 2,
 * point 7) — sent to that player only, as the reply to a {@link HallSchematicRequestPayload}. Only the cells that do
 * not match the intact hall yet, each with the block that belongs there; at most {@link #MAX_CELLS} of them, the
 * nearest to the player when there are more.
 *
 * @param dimension the level the hall stands in
 * @param core      the core the schematic was asked at — the client's key for it
 * @param available whether the town has a hall with an intact template; false — nothing to show, the client turns the
 *                  schematic off
 * @param missing   how many cells of the hall do not match, all of them, whether sent or not
 * @param cells     the cells sent, bottom layer first
 */
public record HallSchematicPayload(ResourceKey<Level> dimension, BlockPos core, boolean available, int missing,
		List<Ghost> cells) implements CustomPacketPayload {

	public static final Type<HallSchematicPayload> TYPE = new Type<>(Industrialization.id("hall_schematic"));

	/** Most cells one reply carries: a town hall counts under 900, so the whole hall fits. */
	public static final int MAX_CELLS = 2048;

	public HallSchematicPayload {
		core = core.immutable();
		cells = List.copyOf(cells.subList(0, Math.min(MAX_CELLS, cells.size())));
	}

	/**
	 * One cell still to build.
	 *
	 * @param pos   where, packed ({@link BlockPos#asLong()})
	 * @param state the block that belongs there, turned with the hall
	 * @param gap   whether the cell is where the town's road network is cut off (R132, MOD-884): drawn highlighted
	 */
	public record Ghost(long pos, BlockState state, boolean gap) {

		/** A cell still to build that is no gap of the network. */
		public Ghost(long pos, BlockState state) {
			this(pos, state, false);
		}
	}

	/** One ghost cell, as the house laying window (MOD-877) carries its house too. */
	public static final StreamCodec<RegistryFriendlyByteBuf, Ghost> GHOST = StreamCodec.composite(
			ByteBufCodecs.LONG, Ghost::pos,
			ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY), Ghost::state,
			ByteBufCodecs.BOOL, Ghost::gap,
			Ghost::new);

	public static final StreamCodec<RegistryFriendlyByteBuf, HallSchematicPayload> CODEC = StreamCodec.composite(
			ResourceKey.streamCodec(Registries.DIMENSION), HallSchematicPayload::dimension,
			BlockPos.STREAM_CODEC, HallSchematicPayload::core,
			ByteBufCodecs.BOOL, HallSchematicPayload::available,
			ByteBufCodecs.VAR_INT, HallSchematicPayload::missing,
			GHOST.apply(ByteBufCodecs.list(MAX_CELLS)), HallSchematicPayload::cells,
			HallSchematicPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

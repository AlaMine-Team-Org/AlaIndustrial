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
 * S2C: the card of one town building (round 2 of 0.1.219, Q2) — sent to a member of the town who right-clicked the
 * house's inlet or the building's plaque, and as the reply to every {@link BuildingCardRequestPayload}. Words only:
 * the building's row of the core's «Construction» tab and a few lines more.
 *
 * @param anchor the block the card was opened from
 * @param core   the town's settlement core: the schematic is asked for at it
 * @param open   open the card, not only update an open one
 * @param row    the building's row: name, state, share, second line, its index as the target and its buttons
 * @param lines  the share restored, who lives in it
 */
public record BuildingCardPayload(BlockPos anchor, BlockPos core, boolean open, ConstructionRow row,
		List<ConstructionRow.Text> lines) implements CustomPacketPayload {

	public static final Type<BuildingCardPayload> TYPE = new Type<>(Industrialization.id("building_card"));

	/** Most lines one card carries. */
	public static final int MAX_LINES = 8;

	public BuildingCardPayload {
		anchor = anchor.immutable();
		core = core.immutable();
		lines = List.copyOf(lines.subList(0, Math.min(MAX_LINES, lines.size())));
	}

	public static final StreamCodec<RegistryFriendlyByteBuf, BuildingCardPayload> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, BuildingCardPayload::anchor,
			BlockPos.STREAM_CODEC, BuildingCardPayload::core,
			ByteBufCodecs.BOOL, BuildingCardPayload::open,
			TownWorksPayload.ROW, BuildingCardPayload::row,
			TownWorksPayload.TEXT.apply(ByteBufCodecs.list(MAX_LINES)), BuildingCardPayload::lines,
			BuildingCardPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

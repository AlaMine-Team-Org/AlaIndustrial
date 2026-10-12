package dev.alaindustrial.network;

import dev.alaindustrial.Industrialization;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * S2C: a block's reach preview (MOD-278 for the mob repeller, generalised in MOD-802 for the vacuum
 * collector) — sent to exactly ONE player. Carries the block position, the server-authoritative reach and
 * what to do with it:
 *
 * <ul>
 *   <li>{@code toggle = true} — the player pressed the preview button: show the zone if hidden, hide it if
 *       shown. The server stays stateless about who is looking at what.</li>
 *   <li>{@code toggle = false} — the player just OPENED this block's screen. Nothing is shown or hidden; the
 *       client only learns which block the open screen belongs to, so the button can render its on/off
 *       state. A client menu has no block position of its own (that side gets
 *       {@code ContainerLevelAccess.NULL}).</li>
 * </ul>
 *
 * <p>{@code reach} is the half-size of the zone cube measured from the CENTRE of the block, in blocks, so
 * each block states its own zone exactly: the repeller's {@code new AABB(pos).inflate(r)} is {@code r + 0.5},
 * the collector's ±5 around its centre is {@code 5}.
 *
 * <p>Same delivery shape as {@link NetworkAnalyzerPayload}: personal, fire-and-forget, cleared client-side by
 * its own rules (block gone / dimension change) rather than by a server timer.
 */
public record ZonePreviewPayload(ResourceKey<Level> dimension, BlockPos pos, double reach, boolean toggle)
		implements CustomPacketPayload {

	public static final Type<ZonePreviewPayload> TYPE = new Type<>(Industrialization.id("zone_preview"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ZonePreviewPayload> CODEC = StreamCodec.composite(
			ResourceKey.streamCodec(Registries.DIMENSION), ZonePreviewPayload::dimension,
			BlockPos.STREAM_CODEC, ZonePreviewPayload::pos,
			ByteBufCodecs.DOUBLE, ZonePreviewPayload::reach,
			ByteBufCodecs.BOOL, ZonePreviewPayload::toggle,
			ZonePreviewPayload::new);

	/**
	 * Send {@code player} the zone of the block at {@code pos} in {@code level}: a toggle for a button press,
	 * a plain announcement when its screen opens. Does nothing off the server.
	 */
	public static void send(Player player, Level level, BlockPos pos, double reach, boolean toggle) {
		if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
			NetworkDispatcher.get().sendToPlayer(serverPlayer,
					new ZonePreviewPayload(serverLevel.dimension(), pos, reach, toggle));
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

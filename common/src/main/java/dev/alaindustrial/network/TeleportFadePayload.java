package dev.alaindustrial.network;

import dev.alaindustrial.Industrialization;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * How dark the jumper's screen should be right now (MOD-106).
 *
 * <p>The warmup lives entirely on the server, so the client has to be told. What it is told is a
 * <b>level, not an event</b> — "the screen is 40% dark", re-sent every tick of the last second,
 * rather than "start fading" / "stop fading".
 *
 * <p>That shape is the whole safety argument. A cancel is not a packet that can be dropped, arrive
 * late, or be missed because the player logged out mid-warmup: it is simply the absence of the next
 * level. The client fades back to clear on its own as soon as the levels stop coming
 * ({@code TeleportFadeHud}), so no failure mode ends with a player stuck staring at a black screen.
 * The server still sends an explicit zero when a jump lands or a warmup is cancelled — that only
 * makes the clearing prompt, it is not what makes it safe.
 */
public record TeleportFadePayload(float strength) implements CustomPacketPayload {
	public static final Type<TeleportFadePayload> TYPE = new Type<>(Industrialization.id("teleport_fade"));

	public static final StreamCodec<RegistryFriendlyByteBuf, TeleportFadePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, TeleportFadePayload::strength,
			TeleportFadePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

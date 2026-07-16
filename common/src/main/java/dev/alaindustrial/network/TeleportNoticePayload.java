package dev.alaindustrial.network;

import dev.alaindustrial.Industrialization;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Why the remote just refused (MOD-093) — a line to show <em>inside</em> the remote's screen.
 *
 * <p>It exists because the action bar does not work here. Every refusal — the station is flat, the
 * teleporter is still recharging, the station is gone — was being written to the action bar, which
 * sits at the bottom of the screen underneath the remote's own 200×190 panel. The player pressed
 * Teleport, nothing happened, and the explanation was hidden behind the very screen they were
 * looking at.
 *
 * <p>Carries a whole {@link Component} rather than a key: the reasons already exist as translatable
 * components with arguments (seconds left, EU), and re-deriving them client-side would mean teaching
 * the client rules that are deliberately the server's alone. It stays translated on the client
 * because a Component crosses the wire unresolved.
 */
public record TeleportNoticePayload(Component message) implements CustomPacketPayload {
	public static final Type<TeleportNoticePayload> TYPE = new Type<>(Industrialization.id("teleport_notice"));

	public static final StreamCodec<RegistryFriendlyByteBuf, TeleportNoticePayload> CODEC = StreamCodec.composite(
			ComponentSerialization.STREAM_CODEC, TeleportNoticePayload::message,
			TeleportNoticePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

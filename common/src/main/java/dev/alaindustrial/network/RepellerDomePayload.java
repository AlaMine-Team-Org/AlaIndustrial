package dev.alaindustrial.network;

import dev.alaindustrial.Industrialization;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * S2C: the Mob Repeller's dome message (MOD-278) — sent to exactly ONE player. Carries the block
 * position, the server-authoritative radius, and what to do with it:
 *
 * <ul>
 *   <li>{@code toggle = true} — the player pressed the dome button: show the dome if hidden, hide it
 *       if shown. The server stays stateless about who is looking at what.</li>
 *   <li>{@code toggle = false} — the player just OPENED this repeller's screen. Nothing is shown or
 *       hidden; the client only learns which block the open screen belongs to, so the button can
 *       render its on/off state. A client menu has no block position of its own (that side gets
 *       {@code ContainerLevelAccess.NULL}), and this one packet is cheaper than teaching the menu
 *       to carry extra data through two loader-specific factories.</li>
 * </ul>
 *
 * <p>Same delivery shape as {@link NetworkAnalyzerPayload}: personal, fire-and-forget, cleared
 * client-side by its own rules (block gone / dimension change) rather than by a server timer.
 */
public record RepellerDomePayload(ResourceKey<Level> dimension, BlockPos pos, int radius, boolean toggle)
		implements CustomPacketPayload {

	public static final Type<RepellerDomePayload> TYPE = new Type<>(Industrialization.id("repeller_dome"));

	public static final StreamCodec<RegistryFriendlyByteBuf, RepellerDomePayload> CODEC = StreamCodec.composite(
			ResourceKey.streamCodec(Registries.DIMENSION), RepellerDomePayload::dimension,
			BlockPos.STREAM_CODEC, RepellerDomePayload::pos,
			ByteBufCodecs.VAR_INT, RepellerDomePayload::radius,
			ByteBufCodecs.BOOL, RepellerDomePayload::toggle,
			RepellerDomePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

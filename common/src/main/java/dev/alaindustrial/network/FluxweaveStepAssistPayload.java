package dev.alaindustrial.network;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.item.wearable.FluxweaveArmorItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Toggle the step assist on the worn Fluxweave leggings (MOD-127).
 *
 * <p><b>Why a packet at all.</b> The other three key mappings in the mod are purely client-side — they
 * flip an overlay and write the client config, and the server never needs to know. This one does need
 * the server: the assist is an attribute modifier on the trousers, and attributes are applied
 * server-side from the stack's components. A custom {@code KeyMapping} is also not part of vanilla's
 * input synchronisation, so the trick the jetpack uses — reading {@code player.input} on both sides —
 * cannot work here. Hence the smallest possible payload: no fields, just "the player pressed it".
 *
 * <p><b>Nothing is trusted.</b> The payload carries no state to tamper with; the server re-reads the
 * leggings actually worn and does nothing at all if they are not ours. A client that spams this can
 * only flip its own trousers back and forth.
 */
public record FluxweaveStepAssistPayload() implements CustomPacketPayload {
	public static final Type<FluxweaveStepAssistPayload> TYPE =
			new Type<>(Industrialization.id("fluxweave_step_assist"));

	/** No payload body — the packet's arrival is the whole message. */
	public static final StreamCodec<RegistryFriendlyByteBuf, FluxweaveStepAssistPayload> CODEC =
			StreamCodec.unit(new FluxweaveStepAssistPayload());

	/**
	 * Flip the flag on the worn leggings and tell the player which way it went. The feedback matters:
	 * step assist is invisible until you walk into a block, so without a message a press would look
	 * like it did nothing — the same reasoning the HUD toggles use.
	 */
	public static void handle(FluxweaveStepAssistPayload payload, ServerPlayer player) {
		FluxweaveArmorItem.toggleStepAssist(player).ifPresent(enabled -> {
			player.sendSystemMessage(Component.translatable(enabled
					? "message.alaindustrial.fluxweave.step_assist.on"
					: "message.alaindustrial.fluxweave.step_assist.off"), true);
			player.level().playSound(null, player.blockPosition(),
					SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.4f, enabled ? 1.2f : 0.8f);
		});
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

package dev.alaindustrial.network;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.item.tool.DrillUpgrades;
import dev.alaindustrial.item.tool.ElectricDrillItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * Switch the column bore on the held drill on or off (MOD-482).
 *
 * <p><b>Why a packet rather than sneak + right-click.</b> The mod's other item toggles ride on
 * {@code Item.use}, which vanilla already synchronises — but that gesture is taken on this line: a
 * plain right-click places a torch (MOD-089) on all three tiers, and sneak + right-click switches Silk
 * Touch on the two tipped ones (MOD-321). The column has to work identically on all three, and the
 * only control that is free on all three is a key. A custom {@code KeyMapping} is not part of vanilla's
 * input synchronisation, so the press has to be sent — the same reasoning as the step assist
 * ({@link FluxweaveStepAssistPayload}).
 *
 * <p><b>Nothing is trusted.</b> The payload has no body; the server re-reads the stack actually in the
 * player's main hand and does nothing unless it is a drill with the upgrade installed. A client that
 * spams this can only flip its own drill back and forth.
 */
public record DrillColumnTogglePayload() implements CustomPacketPayload {
	public static final Type<DrillColumnTogglePayload> TYPE =
			new Type<>(Industrialization.id("drill_column_toggle"));

	/** No payload body — the packet's arrival is the whole message. */
	public static final StreamCodec<RegistryFriendlyByteBuf, DrillColumnTogglePayload> CODEC =
			StreamCodec.unit(new DrillColumnTogglePayload());

	/**
	 * Flip the mode on the held drill and say which way it went on the action bar.
	 *
	 * <p>Three answers, all deliberate: a drill without the upgrade says so (otherwise the key looks
	 * broken to a player who has not been to the Upgrade Table yet), anything else in hand is silent
	 * (the key belongs to the drill, not to the player), and a successful flip gets the same copper-bulb
	 * click the Silk Touch toggle uses — one mode switch, one sound, wherever the player learned it.
	 */
	public static void handle(DrillColumnTogglePayload payload, ServerPlayer player) {
		ItemStack drill = player.getMainHandItem();
		if (!(drill.getItem() instanceof ElectricDrillItem)) {
			return;
		}
		if (!DrillUpgrades.has(drill, DrillUpgrades.COLUMN_BORE)) {
			player.sendSystemMessage(
					Component.translatable("message.alaindustrial.drill_column.not_installed")
							.withStyle(ChatFormatting.GRAY),
					true);
			return;
		}
		boolean nowOn = !ElectricDrillItem.isColumnEnabled(drill);
		ElectricDrillItem.setColumnEnabled(drill, nowOn);
		player.sendSystemMessage(
				Component.translatable(nowOn
						? "message.alaindustrial.drill_column.on"
						: "message.alaindustrial.drill_column.off")
						.withStyle(nowOn ? ChatFormatting.AQUA : ChatFormatting.GRAY),
				true);
		player.level().playSound(null, player.blockPosition(),
				nowOn ? SoundEvents.COPPER_BULB_TURN_ON : SoundEvents.COPPER_BULB_TURN_OFF,
				SoundSource.PLAYERS, 0.7f, nowOn ? 1.15f : 0.9f);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

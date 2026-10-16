package dev.alaindustrial.network;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.item.tool.DrillBore;
import dev.alaindustrial.item.tool.DrillBoreMode;
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
 * Switch the bore mode of the held drill (MOD-482 column on/off; MOD-821 cycles column and row).
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
 * player's main hand and does nothing unless it is a drill. A client that
 * spams this can only flip its own drill back and forth.
 */
public record DrillColumnTogglePayload() implements CustomPacketPayload {
	public static final Type<DrillColumnTogglePayload> TYPE =
			new Type<>(Industrialization.id("drill_column_toggle"));

	/** No payload body — the packet's arrival is the whole message. */
	public static final StreamCodec<RegistryFriendlyByteBuf, DrillColumnTogglePayload> CODEC =
			StreamCodec.unit(new DrillColumnTogglePayload());

	/**
	 * Switch the held drill to its next bore mode and say which one it is now on the action bar.
	 *
	 * <p>With both modules installed the key cycles cross, column, row, single (MOD-821); with one it
	 * flips that module on and off; with none the drill says so (otherwise the key looks broken to a
	 * player who has not been to the Upgrade Table yet). Anything else in hand is silent (the key belongs
	 * to the drill, not to the player), and a successful switch gets the same copper-bulb click the Silk
	 * Touch toggle uses — one mode switch, one sound, wherever the player learned it.
	 */
	public static void handle(DrillColumnTogglePayload payload, ServerPlayer player) {
		ItemStack drill = player.getMainHandItem();
		if (!(drill.getItem() instanceof ElectricDrillItem)) {
			return;
		}
		boolean column = DrillUpgrades.has(drill, DrillUpgrades.COLUMN_BORE);
		boolean row = DrillUpgrades.has(drill, DrillUpgrades.ROW_BORE);
		if (!column && !row) {
			player.sendSystemMessage(
					Component.translatable("message.alaindustrial.drill_mode.not_installed")
							.withStyle(ChatFormatting.GRAY),
					true);
			return;
		}
		if (column && row) {
			DrillBoreMode next = DrillBore.boreMode(drill).next();
			DrillBore.setColumnEnabled(drill, next.column());
			DrillBore.setRowEnabled(drill, next.row());
		} else if (column) {
			DrillBore.setColumnEnabled(drill, !DrillBore.isColumnEnabled(drill));
		} else {
			DrillBore.setRowEnabled(drill, !DrillBore.isRowEnabled(drill));
		}
		DrillBoreMode mode = DrillBore.boreMode(drill);
		boolean nowOn = mode != DrillBoreMode.SINGLE;
		player.sendSystemMessage(
				Component.translatable("message.alaindustrial.drill_mode." + mode.key())
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

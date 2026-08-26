package dev.alaindustrial.item.teleport;

import dev.alaindustrial.block.TeleporterBlock;
import dev.alaindustrial.block.entity.TeleporterBlockEntity;
import dev.alaindustrial.item.misc.HintItem;
import dev.alaindustrial.teleporter.TeleportEngine;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Random Jump Chip (MOD-116) — the station's one permanent upgrade, fitted by hand.
 *
 * <p><b>Shift-right-click, not right-click, and that is not a style choice.</b> Vanilla's
 * {@code ServerPlayerGameMode.useItemOn} runs the BLOCK's interaction first, and
 * {@link dev.alaindustrial.block.TeleporterBlock} answers a plain right-click by opening the
 * station's screen and returning a consuming result — so an ordinary click with this chip in hand
 * would never reach {@link #useOn} at all and the chip would look broken. Sneaking makes vanilla
 * skip block interaction entirely (verified against the 26.2 bytecode, the same rule the wrench and
 * the cable rely on), which is what lets the item be heard. It is also the gesture the player
 * already knows on this exact block: shift-right-click is how the Teleporter Remote binds it.
 *
 * <p>The chip is consumed and never given back. Nothing about a fitted station can hand one out
 * again, so a base cannot pass one upgrade around and pretend to have several — see
 * {@link TeleporterBlockEntity#setRtpModule}.
 */
public class RtpChipItem extends HintItem {

	public RtpChipItem(Properties properties, String... hintKeys) {
		super(properties, hintKeys);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		// Checked on both sides: a chip poked at dirt should behave like an ordinary item in hand,
		// not swallow the click. Only a station is ours to answer for.
		if (!(level.getBlockEntity(pos) instanceof TeleporterBlockEntity station)) {
			return InteractionResult.PASS;
		}
		if (!(level instanceof ServerLevel) || !(context.getPlayer() instanceof ServerPlayer player)) {
			return InteractionResult.SUCCESS;
		}

		// Same gate as binding the remote to this station: a private station answers only to its owner.
		if (!station.allowsAccess(player.getUUID())) {
			return deny(player, TeleportEngine.Denial.NO_ACCESS.message());
		}
		if (station.hasRtpModule()) {
			// Told, not silently eaten: fitting a second chip would otherwise cost a chip for nothing.
			return deny(player, Component.translatable("alaindustrial.teleporter.rtp_module_present"));
		}

		station.setRtpModule(true);
		// Light the marker on the block itself, so the upgrade is visible without opening anything.
		TeleporterBlock.showUpgraded(level, pos, level.getBlockState(pos));
		context.getItemInHand().consume(1, player);
		// The same "locked in" chime the remote plays when it binds this station — one gesture, one
		// sound vocabulary, and no new asset for either.
		level.playSound(null, pos, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 0.7f, 1.1f);
		player.sendSystemMessage(Component.translatable("alaindustrial.teleporter.rtp_module_installed")
				.withStyle(ChatFormatting.GREEN), true);
		return InteractionResult.SUCCESS;
	}

	/** A refusal the player can read, on the action bar, without the click counting as nothing. */
	private static InteractionResult deny(ServerPlayer player, Component reason) {
		player.sendSystemMessage(reason.copy().withStyle(ChatFormatting.RED), true);
		return InteractionResult.SUCCESS;
	}
}

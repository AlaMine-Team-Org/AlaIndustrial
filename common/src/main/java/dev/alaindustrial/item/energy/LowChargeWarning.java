package dev.alaindustrial.item.energy;

import dev.alaindustrial.item.ToolConfig;
import dev.alaindustrial.item.energy.PoweredItem.LowChargeProfile;
import dev.alaindustrial.registry.ModSounds;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The low-charge warning of a powered item (MOD-652): a short beep and a line above the hotbar, sent to the one
 * player whose item is running out. {@link ItemEnergy#spend} calls it after every debit; {@link LowChargeRule}
 * decides, {@link LowChargeLedger} remembers when each player was last told, and this class delivers.
 *
 * <p><b>The sound is addressed to one player, not played into the world.</b> {@code Player#playSound} on the
 * server excludes the player themselves and reaches everyone else — the reverse of what a personal warning
 * needs. A targeted {@link ClientboundSoundPacket} is the mod's answer (see {@code GeigerTicker}); the client
 * takes the subtitle from the sound's own id.
 */
public final class LowChargeWarning {

	/** Gain of the beep. Quiet on purpose: it is heard on top of the jetpack's own exhaust. */
	private static final float BEEP_VOLUME = 0.7f;
	private static final float PITCH = 1.0f;

	private static final LowChargeLedger LEDGER = new LowChargeLedger();

	private LowChargeWarning() {
	}

	/**
	 * The player to warn about a debit of {@code stack}, or null — the cheap gate {@link ItemEnergy#spend} runs
	 * before it reads the charge, because the jetpack spends every tick. Null for anything that is not a real,
	 * connected player, for an item with no profile, and when the warning is switched off.
	 */
	static @Nullable ServerPlayer watcher(ItemStack stack, @Nullable Entity owner) {
		if (!(owner instanceof ServerPlayer player) || !ToolConfig.lowChargeWarnEnabled
				|| player.connection == null) {
			return null;
		}
		return stack.getItem() instanceof PoweredItem powered && powered.lowChargeProfile() != null ? player : null;
	}

	/** Called by {@link ItemEnergy#spend} after the charge dropped from {@code before}. */
	static void afterSpend(ServerPlayer player, ItemStack stack, long before) {
		Item item = stack.getItem();
		LowChargeProfile profile = ((PoweredItem) item).lowChargeProfile();
		long after = ItemEnergy.get(stack);
		long capacity = ItemEnergy.capacity(stack);
		int signal = LEDGER.decide(player.getUUID(), item, before, after, capacity, ToolConfig.lowChargeWarnPercent,
				profile.depletedBelow((PoweredItem) item), player.level().getGameTime(), profile.repeatTicks());
		if (signal != LowChargeRule.NONE) {
			send(player, signal, stack.getHoverName(), after, capacity);
		}
	}

	private static void send(ServerPlayer player, int signal, Component name, long after, long capacity) {
		boolean depleted = signal == LowChargeRule.DEPLETED;
		Component line = depleted
				? Component.translatable("message.alaindustrial.charge_depleted", name).withStyle(ChatFormatting.RED)
				// The percent goes in as a finished string: a literal "%" in a lang value is a format directive.
				: Component.translatable("message.alaindustrial.low_charge", name,
						LowChargeRule.percent(after, capacity) + "%").withStyle(ChatFormatting.YELLOW);
		player.sendOverlayMessage(line);
		SoundEvent sound = depleted ? ModSounds.CHARGE_DEPLETED.get() : ModSounds.LOW_CHARGE_BEEP.get();
		player.connection.send(new ClientboundSoundPacket(Holder.direct(sound), SoundSource.PLAYERS,
				player.getX(), player.getY(), player.getZ(), BEEP_VOLUME, PITCH, player.getRandom().nextLong()));
	}

	/** A player left: drop their record. */
	public static void forget(UUID player) {
		LEDGER.forget(player);
	}

	/** Drop every record — used when the server stops, so nothing crosses into the next world. */
	public static void clear() {
		LEDGER.clear();
	}
}

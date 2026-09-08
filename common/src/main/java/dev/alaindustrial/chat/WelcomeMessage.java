package dev.alaindustrial.chat;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.WelcomeMessageState;
import java.net.URI;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The mod's hello, written into chat once per world (MOD-596).
 *
 * <p>Two lines. The first is signed {@code [Ala Industrial]} through {@link ModChat#line}, because it
 * is the mod talking and chat is shared ground. The second deliberately is NOT: it is the same voice
 * finishing the same thought, and a second tag one line down reads as a second speaker.
 *
 * <p>Loader-neutral on purpose — one static method here, and a thin join hook on each loader, exactly
 * as {@code GuideBookGiver} is wired.
 *
 * <h2>The link</h2>
 * {@code ClickEvent} in 26.2 is an interface with a record per action, not the old
 * {@code new ClickEvent(Action, String)} — {@code OPEN_URL} is {@link ClickEvent.OpenUrl}, and it
 * carries a {@link URI} rather than a string. Verified against the 26.2 sources before writing, since
 * the mod had no {@code ClickEvent} anywhere to copy from.
 *
 * <p>The invite is a compile-time constant rather than a config value. A server owner who does not
 * want their players pointed at our Discord turns the whole greeting off
 * ({@link Config#welcomeMessageEnabled}); handing them a field to put THEIR link in would make the
 * mod's own signature line advertise somebody else.
 */
public final class WelcomeMessage {

	/** The mod's Discord. Kept next to the other places that carry it — see {@code docs/DISCORD.md}. */
	private static final String DISCORD_INVITE = "https://discord.gg/ky7cFDF8JD";

	private WelcomeMessage() {
	}

	/**
	 * Greet the world if it has not been greeted yet. Safe to call on every join: the flag is claimed
	 * atomically by the first caller and every later one falls out immediately.
	 */
	public static void sendIfNeeded(ServerPlayer player) {
		if (!Config.welcomeMessageEnabled) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return;
		}
		WelcomeMessageState state = server.getDataStorage().computeIfAbsent(WelcomeMessageState.TYPE);
		if (!state.claim()) {
			return;
		}
		// false = real chat, not the action bar. The action bar is transient and belongs to what the
		// player is holding; a greeting that scrolls away in two seconds has not been read.
		// The world-load palette (gold tag, dark green body) — the same look the Ore Vein Miner compat
		// line has printed on load since it shipped, and the one the owner asked the greeting to match.
		player.sendSystemMessage(ModChat.loadLine(
				Component.translatable("message.alaindustrial.welcome.line1")
						.withStyle(ChatFormatting.DARK_GREEN)), false);
		player.sendSystemMessage(discordLine(), false);
	}

	/** The clickable second line: blue, underlined, opens the invite through the client's own warning. */
	private static Component discordLine() {
		return Component.translatable("message.alaindustrial.welcome.discord")
				.withStyle(Style.EMPTY
						.withColor(ChatFormatting.BLUE)
						.withUnderlined(true)
						.withClickEvent(new ClickEvent.OpenUrl(URI.create(DISCORD_INVITE))));
	}
}

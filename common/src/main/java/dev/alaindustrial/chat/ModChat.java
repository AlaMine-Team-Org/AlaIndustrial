package dev.alaindustrial.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The one way this mod signs a line it writes into chat (MOD-522).
 *
 * <p><b>Why a tag at all.</b> Chat is shared ground: the server, the vanilla game and every other
 * mod write into the same column. An unsigned grey sentence about a greenhouse is indistinguishable
 * from a server plugin's, and the player has no way to tell who is talking to them. The tag says it
 * in three words and costs one line of code per call site.
 *
 * <p><b>Chat only — never the action bar.</b> The overlay above the hotbar is a different surface:
 * it is narrow, it is transient, and it already belongs to whatever the player is holding
 * ({@code sendSystemMessage(…, true)}). A brand tag there would eat a third of the width to repeat
 * something the player learns once. All 26 action-bar calls in the mod stay bare on purpose; the
 * four that write real chat lines go through here.
 *
 * <p><b>The name is {@code command.alaindustrial.name}, deliberately reused.</b> It is the same
 * string the {@code /ala} header prints, and the mod's name is a brand rather than prose — two keys
 * holding "Ala Industrial" would be two things to keep equal, and the pair would drift the first
 * time somebody shortened one of them.
 *
 * <p>The palette is the project's: AQUA for the mod's own voice, DARK_GRAY for punctuation — the
 * same two roles {@code AlaCommandCommon} gives them.
 */
public final class ModChat {

	private ModChat() {
	}

	/** Brackets and other punctuation — the quietest tone in the palette. */
	private static final ChatFormatting BRACKET = ChatFormatting.DARK_GRAY;

	/** The mod speaking; AQUA is this project's "the machinery is talking" accent. */
	private static final ChatFormatting NAME = ChatFormatting.AQUA;

	/**
	 * {@code [Ala Industrial] <body>}, ready for {@code sendSystemMessage(…, false)}.
	 *
	 * <p>Built on an {@link Component#empty()} root rather than on the opening bracket: siblings
	 * inherit the style of the component they are appended to, so hanging the body off a DARK_GRAY
	 * bracket would tint every uncoloured word of it dark grey. An empty root has nothing to inherit.
	 */
	public static MutableComponent line(Component body) {
		return Component.empty()
				.append(Component.literal("[").withStyle(BRACKET))
				.append(Component.translatable("command.alaindustrial.name").withStyle(NAME))
				.append(Component.literal("]").withStyle(BRACKET))
				.append(Component.literal(" "))
				.append(body);
	}

	/** The louder tag the mod's world-load announcements wear — the whole bracket group in gold. */
	private static final ChatFormatting LOAD_TAG = ChatFormatting.GOLD;

	/**
	 * {@code [Ala Industrial] <body>} in the world-load palette: the tag whole and gold rather than
	 * grey-bracketed aqua.
	 *
	 * <p><b>Why the mod signs itself two ways.</b> {@link #line} is the machinery talking to a player
	 * who is already playing — a greenhouse, a reactor, a tool — and it stays quiet on purpose, because
	 * it lands in the middle of a busy chat column. This one is for the handful of lines that arrive
	 * when a world opens, where chat is empty and the message is the mod introducing itself. The look
	 * is not invented here: the Ore Vein Miner compat line has printed exactly this gold tag from a
	 * datapack {@code tellraw} since it shipped, a {@code .mcfunction} cannot call this class, and the
	 * owner asked for the greeting to match the line he actually sees on load.
	 *
	 * <p>The body's colour is the caller's, not this method's — the tag is the signature, the sentence
	 * is the message.
	 */
	public static MutableComponent loadLine(Component body) {
		return Component.empty()
				.append(Component.literal("[").withStyle(LOAD_TAG))
				.append(Component.translatable("command.alaindustrial.name").withStyle(LOAD_TAG))
				.append(Component.literal("]").withStyle(LOAD_TAG))
				.append(Component.literal(" "))
				.append(body);
	}
}

package dev.alaindustrial.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * A station a Teleporter Remote is bound to (MOD-092): where it is, and what the player calls it.
 *
 * <p>This is vanilla's {@link net.minecraft.core.GlobalPos} plus a name — same codec shape, on
 * purpose. It stores {@code dim} even though MOD-092 is same-dimension only: the cross-dimension
 * upgrade is a policy change in {@code TeleportEngine}, and keeping the dimension here means that
 * upgrade needs no data migration on players' existing remotes.
 *
 * <p>{@link #name} is player-supplied (renamed through the MOD-093 GUI), so it is length-capped on
 * the wire — an unbounded string from a client is somebody else's exploit. An empty name means the
 * player never picked one, and the point shows up as "Teleporter {@link #number}" instead. That
 * indirection is what lets the default name be translated: a name baked into the component at bind
 * time would be frozen in whatever language the server happened to run.
 */
public record TeleportPoint(ResourceKey<Level> dim, BlockPos pos, String name, int number) {
	/**
	 * Max characters a <b>new</b> name may have; the server enforces it, never the client.
	 *
	 * <p>This is a cap on <i>input</i>, not on looks — what keeps a name inside the remote's list well
	 * is the screen measuring it in pixels and clipping, since 24 Chinese characters are about twice
	 * as wide as 24 Latin ones and no single character count can be right for both.
	 *
	 * <p>Lowering this is safe. Lowering {@link #NAME_WIRE_LIMIT} is not — see there.
	 */
	public static final int MAX_NAME_LENGTH = 24;

	/**
	 * Longest name the wire will carry.
	 *
	 * <p><b>This number may never shrink.</b> It is not a design choice, it is a floor: it must stay
	 * at or above the longest name any already-saved remote could hold. A name is stored in an item
	 * component, and vanilla encodes a player's inventory with these codecs on every sync — so a limit
	 * below what an existing save holds makes {@code Utf8String.write} throw, which kills the
	 * connection and locks the player out of their own world. That is exactly what happened when
	 * {@link #MAX_NAME_LENGTH} was lowered 32 → 24 while it was also the wire limit: a remote named
	 * with the old 32 characters could no longer be sent to the client at all.
	 *
	 * <p>So the two caps are separate on purpose. Tightening what a player may type is a UI decision
	 * and costs nothing; tightening what the wire accepts is a save-breaking change. Old over-long
	 * names stay valid and simply clip on screen until renamed.
	 */
	public static final int NAME_WIRE_LIMIT = 32;

	/** A point the player has named; auto-numbering does not apply to it. */
	public TeleportPoint(ResourceKey<Level> dim, BlockPos pos, String name) {
		this(dim, pos, name, 0);
	}

	public static final Codec<TeleportPoint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Level.RESOURCE_KEY_CODEC.fieldOf("dim").forGetter(TeleportPoint::dim),
			BlockPos.CODEC.fieldOf("pos").forGetter(TeleportPoint::pos),
			Codec.STRING.fieldOf("name").forGetter(TeleportPoint::name),
			Codec.INT.optionalFieldOf("number", 0).forGetter(TeleportPoint::number))
			.apply(instance, TeleportPoint::new));

	public static final StreamCodec<ByteBuf, TeleportPoint> STREAM_CODEC = StreamCodec.composite(
			ResourceKey.streamCodec(Registries.DIMENSION), TeleportPoint::dim,
			BlockPos.STREAM_CODEC, TeleportPoint::pos,
			ByteBufCodecs.stringUtf8(NAME_WIRE_LIMIT), TeleportPoint::name,
			ByteBufCodecs.VAR_INT, TeleportPoint::number,
			TeleportPoint::new);

	/**
	 * What the player reads — their own name, or the translated default.
	 *
	 * <p>Always a {@link Component}, never a resolved string: the default has to reach the client
	 * untranslated so it lands in the client's language rather than the server's.
	 */
	public Component displayName() {
		return name.isEmpty()
				? Component.translatable("alaindustrial.teleporter.default_name", number)
				: Component.literal(name);
	}

	/**
	 * Trim a name to the cap; used wherever a name arrives from outside (rename, default naming).
	 *
	 * <p>Cuts on a code-point boundary, never mid-character. A blind {@code substring(0, 24)} can land
	 * between the two halves of a surrogate pair and leave a lone surrogate in the component — which
	 * is not a valid string to encode, and would be a client's cheapest way to hand the server
	 * something broken. Emoji and any other astral-plane name shorten cleanly instead.
	 */
	public static String clampName(String raw) {
		String trimmed = raw == null ? "" : raw.trim();
		if (trimmed.length() <= MAX_NAME_LENGTH) {
			return trimmed;
		}
		int end = MAX_NAME_LENGTH;
		if (Character.isHighSurrogate(trimmed.charAt(end - 1))) {
			end--; // that char is the opening half of a pair whose partner is being cut off
		}
		return trimmed.substring(0, end);
	}
}

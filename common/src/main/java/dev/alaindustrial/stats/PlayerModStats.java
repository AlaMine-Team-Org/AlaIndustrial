package dev.alaindustrial.stats;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * The per-player career statistics of the mod (MOD-133), stored as a data attachment on the player
 * and synced to its owner for the dashboard. Immutable on purpose: attachment persistence and sync
 * only fire on {@code setAttached}/{@code modifyAttached}, so every update produces a fresh record
 * (mutating a shared instance in place would silently skip both). {@link PlayerStatsTracker}
 * accumulates deltas in memory and folds them into a new record on flush.
 *
 * <p><b>XP is not a field.</b> It is derived from the two career EU totals — see {@link LevelMath#xpOf} — so
 * tuning the rates can never lose or truncate XP (career EU is the ground truth, XP is a view of it).
 * {@link #highestLevelReached} is stored, however, so a balance change never demotes a rank a player
 * already earned; the displayed level is {@code max(levelForXp(...), highestLevelReached)}.
 *
 * <p><b>Two consumption counters, not one (MOD-361).</b> {@link #euUsefulConsumedTotal} is completed
 * machine work and is the XP term; {@link #euOtherSpentTotal} is EU the player spent on everything
 * else that is not "work" — today only teleport jumps. They are separate because the dashboard and
 * the progression need different answers to "was this EU consumed": the player wants every EU they
 * burned to show up under "Consumed" ({@link #euConsumedTotal}), while MOD-133 deliberately keeps
 * jumps (like charging a battery box or pumping oil) out of mastery. Folding the two would have made
 * "spent" and "earned XP for" the same number and silently turned teleport hops into a levelling
 * mechanic.
 *
 * <p>Every Codec field is optional with a zero/empty default, so adding a field later still reads old
 * saves. {@code producedByGenerator} keys are block registry ids (there are only ~9 generator types,
 * so no top-N cap is needed).
 */
public record PlayerModStats(
		long euProducedTotal,
		long euUsefulConsumedTotal,
		int highestLevelReached,
		Map<Identifier, Long> producedByGenerator,
		long activeTicks,
		long euOtherSpentTotal) {

	/** A brand-new player's stats: everything zero. */
	public static final PlayerModStats EMPTY = new PlayerModStats(0L, 0L, 0, Map.of(), 0L, 0L);

	/** Map-codec form; drives both {@link #CODEC} (Fabric persistent) and NeoForge {@code serialize(MapCodec)}. */
	public static final MapCodec<PlayerModStats> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.LONG.optionalFieldOf("euProducedTotal", 0L).forGetter(PlayerModStats::euProducedTotal),
			Codec.LONG.optionalFieldOf("euUsefulConsumedTotal", 0L).forGetter(PlayerModStats::euUsefulConsumedTotal),
			Codec.INT.optionalFieldOf("highestLevelReached", 0).forGetter(PlayerModStats::highestLevelReached),
			Codec.unboundedMap(Identifier.CODEC, Codec.LONG)
					.optionalFieldOf("producedByGenerator", Map.of()).forGetter(PlayerModStats::producedByGenerator),
			Codec.LONG.optionalFieldOf("activeTicks", 0L).forGetter(PlayerModStats::activeTicks),
			Codec.LONG.optionalFieldOf("euOtherSpentTotal", 0L).forGetter(PlayerModStats::euOtherSpentTotal)
	).apply(instance, PlayerModStats::new));

	public static final Codec<PlayerModStats> CODEC = MAP_CODEC.codec();

	public static final StreamCodec<RegistryFriendlyByteBuf, PlayerModStats> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, PlayerModStats::euProducedTotal,
			ByteBufCodecs.VAR_LONG, PlayerModStats::euUsefulConsumedTotal,
			ByteBufCodecs.VAR_INT, PlayerModStats::highestLevelReached,
			ByteBufCodecs.map(HashMap::new, Identifier.STREAM_CODEC, ByteBufCodecs.VAR_LONG),
			PlayerModStats::producedByGenerator,
			ByteBufCodecs.VAR_LONG, PlayerModStats::activeTicks,
			ByteBufCodecs.VAR_LONG, PlayerModStats::euOtherSpentTotal,
			PlayerModStats::new);

	/** Canonical constructor defensively copies the map so the record stays truly immutable. */
	public PlayerModStats {
		producedByGenerator = Map.copyOf(producedByGenerator);
	}

	/**
	 * Career XP: the machine term plus the generator trickle. Both divisors are clamped to ≥1 (config
	 * already guards them; this keeps the pure function total).
	 *
	 * <p>{@link #euOtherSpentTotal} is deliberately absent — it is spending, not work (MOD-361).
	 */
	public long xp(int euPerXp, int euPerXpGenerated) {
		return LevelMath.xpOf(euUsefulConsumedTotal, euProducedTotal, euPerXp, euPerXpGenerated);
	}

	/**
	 * Every EU this player consumed, whatever it went on — the number shown as "Consumed" in the
	 * dashboard (MOD-361). The player's question there is "where did my energy go", not "what earned
	 * me mastery", so machine work and jumps are one figure; {@link #xp} keeps them apart instead.
	 */
	public long euConsumedTotal() {
		return euUsefulConsumedTotal + euOtherSpentTotal;
	}
}

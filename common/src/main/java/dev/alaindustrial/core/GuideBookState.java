package dev.alaindustrial.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alaindustrial.Industrialization;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Per-world ledger of players who have already been auto-given the Guide Book (MOD-067). Stored as a
 * {@code Set<UUID>} in per-save {@link SavedData} on the server-global storage, so it survives
 * death/respawn/dimension changes and never double-gives on relog. A player absent from the set has
 * not received the book yet — which correctly covers players who already existed before the mod (or
 * this feature) was added: they get it once on their next join.
 *
 * <p><b>26.2 API (verified against sources, MOD-067 audit):</b> {@link SavedData} carries only the
 * dirty flag; serialization is Codec-based via {@link SavedDataType} (NOT {@code ValueInput}/
 * {@code ValueOutput} — that is the {@code BlockEntity} API — and NOT {@code CompoundTag}). The
 * storage class is {@code SavedDataStorage} (renamed from {@code DimensionDataStorage}); access it
 * via {@code server.getDataStorage().computeIfAbsent(TYPE)} for the server-global instance.
 */
public class GuideBookState extends SavedData {
	public static final Codec<GuideBookState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC_SET.optionalFieldOf("given", Set.of()).forGetter(state -> state.given)
	).apply(instance, GuideBookState::new));

	// SavedDataType requires a DataFixTypes; our tag has no legacy schema to fix and the fixer only
	// runs when the stored data version < current (never for our own writes, which stamp the current
	// version), so any valid value is a no-op here. LEVEL is the broad, always-present choice.
	public static final SavedDataType<GuideBookState> TYPE = new SavedDataType<>(
			Industrialization.id("guide_book_given"), GuideBookState::new, CODEC, DataFixTypes.LEVEL);

	private final Set<UUID> given;

	public GuideBookState() {
		this(Set.of());
	}

	public GuideBookState(Set<UUID> given) {
		this.given = new HashSet<>(given);
	}

	/** Record that this player has now been given the book. Returns true if this is the first time. */
	public boolean markGiven(UUID id) {
		if (given.add(id)) {
			setDirty();
			return true;
		}
		return false;
	}

	/** Whether this player has already been auto-given the book. */
	public boolean hasReceived(UUID id) {
		return given.contains(id);
	}
}

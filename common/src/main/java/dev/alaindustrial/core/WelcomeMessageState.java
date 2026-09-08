package dev.alaindustrial.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alaindustrial.Industrialization;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Whether this world has already been greeted (MOD-596). One boolean for the whole save, in per-world
 * {@link SavedData}, so the welcome survives {@code /reload}, a server restart and a change of players.
 *
 * <p><b>Per-world, not per-player</b> — and that is the difference from {@link GuideBookState}, which
 * is otherwise the same pattern. The book is a thing each player must actually receive, so it needs a
 * ledger of who has one. The greeting is a hello from the mod to the world: on a server the second
 * player to log in has not missed anything, they have joined a place that was already introduced. The
 * decision is Terralith's, and the owner's.
 *
 * <p>A world the mod was added to later has no flag at all, so it is greeted once on the next join —
 * that falls out of the mechanism rather than needing a rule of its own.
 *
 * <p><b>26.2 API</b> (same verification as {@link GuideBookState}): {@link SavedData} carries only the
 * dirty flag, serialization is Codec-based through {@link SavedDataType}, and the server-global
 * instance comes from {@code server.getDataStorage().computeIfAbsent(TYPE)}.
 */
public class WelcomeMessageState extends SavedData {

	public static final Codec<WelcomeMessageState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.optionalFieldOf("greeted", false).forGetter(state -> state.greeted)
	).apply(instance, WelcomeMessageState::new));

	// SavedDataType needs a DataFixTypes; our tag has no legacy schema and the fixer only runs when the
	// stored version is older than the current one (never for our own writes), so any valid value is a
	// no-op. LEVEL is the broad, always-present choice — the same one GuideBookState takes.
	public static final SavedDataType<WelcomeMessageState> TYPE = new SavedDataType<>(
			Industrialization.id("welcome_greeted"), WelcomeMessageState::new, CODEC, DataFixTypes.LEVEL);

	private boolean greeted;

	public WelcomeMessageState() {
		this(false);
	}

	public WelcomeMessageState(boolean greeted) {
		this.greeted = greeted;
	}

	/** Claim the greeting for this world. Returns true exactly once, for the first caller ever. */
	public boolean claim() {
		if (greeted) {
			return false;
		}
		greeted = true;
		setDirty();
		return true;
	}
}

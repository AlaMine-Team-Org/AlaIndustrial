package dev.alaindustrial.settlement.works;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The save form of a {@link WorkJournal} (MOD-860): a list of lines, oldest first, each with its game time, kind,
 * subject and count. Every field but the kind has a default, and a line of a kind this build does not know is dropped
 * instead of failing the block it is saved in — a journal is history, losing a line of it loses nothing that matters.
 *
 * <p>Kept apart from the journal so the journal stays Minecraft-free for the L1 lane; the codec is held by the
 * {@code town_build_materials} game tests.
 */
public final class WorkJournalCodec {

	private record SavedEntry(long gameTime, String kind, String subject, int count) {
	}

	private static final Codec<SavedEntry> ENTRY = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.optionalFieldOf("time", 0L).forGetter(SavedEntry::gameTime),
			Codec.STRING.fieldOf("kind").forGetter(SavedEntry::kind),
			Codec.STRING.optionalFieldOf("subject", "").forGetter(SavedEntry::subject),
			Codec.INT.optionalFieldOf("count", 0).forGetter(SavedEntry::count)
	).apply(i, SavedEntry::new));

	/** A journal as a list of its lines. */
	public static final Codec<WorkJournal> CODEC = ENTRY.listOf().xmap(WorkJournalCodec::read,
			WorkJournalCodec::write);

	private WorkJournalCodec() {
	}

	private static WorkJournal read(List<SavedEntry> saved) {
		List<WorkJournal.Entry> entries = new ArrayList<>();
		for (SavedEntry entry : saved) {
			Optional<WorkJournal.Kind> kind = WorkJournal.Kind.byName(entry.kind());
			kind.ifPresent(k -> entries.add(new WorkJournal.Entry(entry.gameTime(), k, entry.subject(),
					entry.count())));
		}
		return new WorkJournal(entries);
	}

	private static List<SavedEntry> write(WorkJournal journal) {
		List<SavedEntry> out = new ArrayList<>();
		for (WorkJournal.Entry entry : journal.entries()) {
			out.add(new SavedEntry(entry.gameTime(), entry.kind().serializedName(), entry.subject(), entry.count()));
		}
		return out;
	}
}

package dev.alaindustrial.block.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alaindustrial.core.structure.ReactorLog;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * How the reactor controller's event log is written to the world (MOD-622).
 *
 * <p><b>Kinds are saved by name.</b> A world outlives the build that wrote it, and an enum's order is only a wire
 * format between one server and its clients; a name this build does not know loads as a neutral line.
 *
 * <p><b>Entry by entry, not as one list.</b> {@code ValueInput.listOrEmpty} drops an entry it cannot read and keeps the
 * rest, where a single list codec would lose the whole log to one bad line. A world written before the log existed
 * has no such key and loads an empty log.
 */
final class ReactorLogStorage {

	static final String ENTRIES = "Log";
	static final String NEXT_SEQ = "LogNextSeq";
	static final String READERS = "LogReaders";
	static final String RUNNING = "LogRunning";
	static final String BARE = "LogBare";
	static final String MELTING = "LogMelting";
	static final String EPISODE_MELTS = "LogMeltEpisode";

	private static final Codec<ReactorLog.Kind> KIND = Codec.STRING.xmap(ReactorLog.Kind::byId, ReactorLog.Kind::id);

	static final Codec<ReactorLog.Entry> ENTRY = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.fieldOf("seq").forGetter(ReactorLog.Entry::seq),
			Codec.LONG.fieldOf("time").forGetter(ReactorLog.Entry::time),
			KIND.fieldOf("kind").forGetter(ReactorLog.Entry::kind),
			Codec.INT.optionalFieldOf("a", 0).forGetter(ReactorLog.Entry::a),
			Codec.INT.optionalFieldOf("b", 0).forGetter(ReactorLog.Entry::b),
			Codec.INT.optionalFieldOf("c", 0).forGetter(ReactorLog.Entry::c),
			Codec.STRING.optionalFieldOf("actor", "").forGetter(ReactorLog.Entry::actor))
			.apply(instance, ReactorLog.Entry::new));

	/** One player's progress through the log. */
	private record Reader(UUID id, int seq) {
	}

	private static final Codec<Reader> READER = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(Reader::id),
			Codec.INT.fieldOf("seq").forGetter(Reader::seq))
			.apply(instance, Reader::new));

	private ReactorLogStorage() {
	}

	static void save(ValueOutput output, ReactorLog log) {
		var entries = output.list(ENTRIES, ENTRY);
		for (ReactorLog.Entry entry : log.entries()) {
			entries.add(entry);
		}
		output.putInt(NEXT_SEQ, log.nextSeq());
		var readers = output.list(READERS, READER);
		log.readers().forEach((id, seq) -> readers.add(new Reader(id, seq)));
	}

	static void load(ValueInput input, ReactorLog log) {
		List<ReactorLog.Entry> entries = new ArrayList<>();
		input.listOrEmpty(ENTRIES, ENTRY).forEach(entries::add);
		Map<UUID, Integer> readers = new LinkedHashMap<>();
		input.listOrEmpty(READERS, READER).forEach(reader -> readers.put(reader.id(), reader.seq()));
		log.restore(entries, input.getIntOr(NEXT_SEQ, 1), readers);
	}

	/**
	 * Takes the log out of the tag chunk loading sends to every player nearby. The log reaches a player only through
	 * the controller's screen, and only while it is open; a hundred lines riding along with every chunk and every
	 * status sync would be traffic for players who never opened it.
	 */
	static void stripFromUpdateTag(CompoundTag tag) {
		for (String key : List.of(ENTRIES, NEXT_SEQ, READERS, RUNNING, BARE, MELTING, EPISODE_MELTS)) {
			tag.remove(key);
		}
	}
}

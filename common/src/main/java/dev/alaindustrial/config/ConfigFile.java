package dev.alaindustrial.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.alaindustrial.Config;
import dev.alaindustrial.Industrialization;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes {@code config/alaindustrial.json} for one {@link KnobRegistry}: versioned, atomic,
 * self-healing, Minecraft-free.
 *
 * <p>The operator's file is characterized byte for byte by {@code ConfigGoldenFileTest}; the knob
 * holders it serves may change (ADR-034), the file may not.
 */
public final class ConfigFile {

	private final KnobRegistry registry;
	private final ConfigSchema schema;

	public ConfigFile(KnobRegistry registry) {
		this.registry = registry;
		this.schema = new ConfigSchema(registry);
	}

	/** The layout and migration ladder this file is read with. */
	public ConfigSchema schema() {
		return schema;
	}

	/**
	 * Load the config file at {@code path}; if it is absent, reset to the compiled defaults and write them.
	 *
	 * <p><b>Versioned (MOD-402):</b> the file carries {@code schemaVersion}. An older one (including a
	 * pre-MOD-402 file, which has no such key and counts as version 0) is walked up the migration ladder
	 * before anything is read, so an existing server keeps every value it had edited. A file from a NEWER
	 * mod build is refused: this build cannot know what its keys mean, so nothing is applied, the balance
	 * falls back to the compiled defaults, the file on disk is left alone, and the reason goes to the log
	 * at WARN.
	 *
	 * <p><b>Atomic:</b> every field is parsed into locals first (a wrong-type value throws before anything is
	 * applied), then committed to the static fields in one block — a single typo in the file can never leave the
	 * live balance half-updated. Staging starts from the builtin defaults, not the live values: a key the file
	 * does not carry is committed at its compiled default (MOD-694, ADR-033).
	 * <b>Self-healing on load:</b> after a successful parse the file is re-serialized in
	 * canonical form (sections + field comments + any newly added mod fields) and rewritten only when its content
	 * actually differs, so existing installs gain the inline comments and the write is idempotent (no churn on
	 * {@code /reload}).
	 * <b>Minecraft-free:</b> uses plain Gson (not {@code net.minecraft.GsonHelper}) so the loader-neutral L1 test
	 * suite, which runs without the Minecraft jar, can exercise the file logic directly.
	 */
	public Config.LoadResult loadFrom(Path path) {
		try {
			if (!Files.exists(path)) {
				// Absent file = the defaults (MOD-694); a failed write puts the old values back, as ERROR promises.
				List<Object> before = registry.liveValues();
				registry.resetToDefaults();
				try {
					Files.writeString(path, canonicalJson());
				} catch (Exception writeError) {
					registry.assignAll(before);
					throw writeError;
				}
				Industrialization.LOGGER.info("[config] wrote defaults to {}", path);
				return Config.LoadResult.DEFAULTS_WRITTEN;
			}
			String raw = Files.readString(path);
			JsonObject o = JsonParser.parseString(raw).getAsJsonObject();

			int fileVersion = schema.readVersion(o);
			if (fileVersion > ConfigSchema.VERSION) {
				Industrialization.LOGGER.warn("[config] {} declares schemaVersion {}, but this build only"
						+ " understands {}. NOTHING from the file was applied — the balance is now the mod's"
						+ " built-in defaults, and the file is left untouched. Downgrade the file or upgrade"
						+ " the mod.", path, fileVersion, ConfigSchema.VERSION);
				registry.resetToDefaults();
				return Config.LoadResult.SCHEMA_TOO_NEW;
			}
			schema.migrate(o, fileVersion, path);

			// --- staging: parse + validate every field into pending commits; a present-but-wrong-type
			//     key throws here, before any static field is touched (atomic all-or-nothing apply below).
			//     Each field is read out of its own section object, resolved once up front so a section
			//     holding something other than an object also fails before any commit. ---
			JsonObject[] bodies = new JsonObject[Section.values().length];
			for (Section section : Section.values()) {
				bodies[section.ordinal()] = sectionBody(o, section);
			}
			JsonObject recordedDefaults = builtinDefaults(o);
			List<String> adopted = new ArrayList<>();
			List<Runnable> pending = new ArrayList<>(registry.fields().size());
			for (ConfigField field : registry.fields()) {
				pending.add(field.stage(bodies[field.section.ordinal()], recordedDefaults, adopted));
			}

			// --- commit: apply all staged values at once (nothing above threw, so this is all-or-nothing). ---
			for (Runnable commit : pending) {
				commit.run();
			}
			Industrialization.LOGGER.info("[config] loaded {}", path);
			if (!adopted.isEmpty()) {
				// One line, not one per knob: a rebalance can move dozens at once, and an admin reading
				// the boot log needs the list, not a wall. Says old -> new so the change is auditable.
				Industrialization.LOGGER.info("[config] {} knob(s) were still at the default this file"
						+ " recorded and now follow this build's new default: {}", adopted.size(),
						String.join(", ", adopted));
			}

			// --- self-heal: rewrite in canonical form (comments + new fields) only when content differs. This
			//     is how an existing comment-less file gains its inline docs, and it stays idempotent on /reload.
			//     A write failure must not fail the (already successful) load, so it is caught separately. ---
			String canonical = canonicalJson();
			if (!normalize(raw).equals(normalize(canonical))) {
				try {
					Files.writeString(path, canonical);
					Industrialization.LOGGER.info("[config] canonicalized {}", path);
				} catch (Exception writeError) {
					Industrialization.LOGGER.error("[config] canonicalize write failed {}: {}", path, writeError.toString());
				}
			}
			return Config.LoadResult.LOADED;
		} catch (Exception e) {
			Industrialization.LOGGER.error("[config] failed to load {}: {}", path, e.toString());
			return Config.LoadResult.ERROR;
		}
	}

	/** Pretty-printed canonical form of the current values, including the {@code _comment_*} field docs. */
	String canonicalJson() {
		// No html escaping: the file is read in a text editor, so = and ' stay literal (MOD-694).
		return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(snapshot());
	}

	/**
	 * The whole current balance as the canonical document: {@code schemaVersion} first, then one object
	 * per {@link Section} in enum order, each holding its fields in registry order with their inline
	 * {@code _comment_} doc above them, and finally the machine-owned
	 * {@link ConfigSchema#BUILTIN_DEFAULTS_KEY} block.
	 *
	 * <p>The defaults block always records THIS build's compiled defaults, never the live values: the
	 * file is being written by this build, so "the default this value was saved against" is this
	 * build's default by definition. That is the whole mechanism — after any write, an untouched knob
	 * equals its recorded default, and the next build that changes that default gets to apply it.
	 */
	JsonObject snapshot() {
		JsonObject root = new JsonObject();
		root.addProperty("_comment_" + ConfigSchema.SCHEMA_VERSION_KEY, ConfigSchema.SCHEMA_VERSION_DOC);
		root.addProperty(ConfigSchema.SCHEMA_VERSION_KEY, ConfigSchema.VERSION);
		for (Section section : Section.values()) {
			JsonObject body = new JsonObject();
			for (ConfigField field : registry.fields()) {
				if (field.section == section) {
					field.write(body);
				}
			}
			root.addProperty("_comment_" + section.id, section.doc);
			root.add(section.id, body);
		}
		JsonObject defaults = new JsonObject();
		for (ConfigField field : registry.fields()) {
			field.writeDefault(defaults);
		}
		root.addProperty("_comment_" + ConfigSchema.BUILTIN_DEFAULTS_KEY, ConfigSchema.BUILTIN_DEFAULTS_DOC);
		root.add(ConfigSchema.BUILTIN_DEFAULTS_KEY, defaults);
		return root;
	}

	/**
	 * The section's object inside {@code file}, or an empty one when the section is absent (every key
	 * in it then takes its builtin default, the same contract a missing key has). A
	 * section key present with a non-object value is a typo in the operator's file and throws, so the
	 * load aborts before any commit instead of silently ignoring a whole group of knobs.
	 */
	private static JsonObject sectionBody(JsonObject file, Section section) {
		JsonElement e = file.get(section.id);
		if (e == null || e.isJsonNull()) {
			return new JsonObject();
		}
		if (e.isJsonObject()) {
			return e.getAsJsonObject();
		}
		throw new IllegalArgumentException("config section '" + section.id + "' must be an object, got " + e);
	}

	/**
	 * The {@link ConfigSchema#BUILTIN_DEFAULTS_KEY} block inside {@code file}, or an empty object when it
	 * is absent or damaged.
	 *
	 * <p><b>Why this one tolerates junk while {@link #sectionBody} throws.</b> A malformed section is
	 * the operator's own data and hiding it would drop a whole group of their edits, so it aborts the
	 * load. This block is the mod's bookkeeping, and the safe reading of a damaged one is "assume every
	 * knob was edited by hand" — which changes nothing at all: every value in the file is kept exactly
	 * as written, and the block is rebuilt by the self-heal. Throwing here would instead refuse the
	 * whole file over a line the operator was told not to touch.
	 */
	private static JsonObject builtinDefaults(JsonObject file) {
		JsonElement e = file.get(ConfigSchema.BUILTIN_DEFAULTS_KEY);
		return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
	}

	/** Ignore line-ending + surrounding-whitespace differences when deciding whether to rewrite the file. */
	private static String normalize(String s) {
		return s.replace("\r\n", "\n").strip();
	}
}

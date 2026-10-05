package dev.alaindustrial.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.alaindustrial.Industrialization;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * The layout of {@code config/alaindustrial.json}: its version, the machine-owned keys beside the
 * sections, and the ladder of migrations that brings an older file up to the current shape (MOD-402).
 */
public final class ConfigSchema {

	/**
	 * Layout version of {@code config/alaindustrial.json}, written into the file as
	 * {@code schemaVersion} and bumped whenever the file's SHAPE or the MEANING of a key changes —
	 * never for adding a knob (a new key is absent-safe and needs no migration).
	 *
	 * <p><b>How to bump it</b> (the whole recipe, deliberately one entry):
	 * <ol>
	 *   <li>Write a {@code private void migrateVNtoVN1(JsonObject file)} here that rewrites the
	 *       document IN PLACE from version {@code N} to {@code N+1} — rename a key, rescale a value,
	 *       move a knob between sections. It receives the document as it stands after every earlier
	 *       step, so each migration only has to know about its own hop.</li>
	 *   <li>Append {@code new Migration(N, this::migrateVNtoVN1)} to {@link #migrations}.</li>
	 *   <li>Raise this constant to {@code N+1}.</li>
	 * </ol>
	 * {@code ConfigSchemaTest} pins the ladder: it must hold exactly one step per version hop, in
	 * ascending order, so a bump without a migration (or a migration without a bump) fails the build
	 * rather than silently skipping a file.
	 *
	 * <p>An OLDER file is migrated step by step and then rewritten in the current shape. A NEWER file
	 * is refused outright — see {@link ConfigFile#loadFrom}.
	 */
	public static final int VERSION = 2;

	/** Json key holding {@link #VERSION}. Absent = a pre-MOD-402 flat file, i.e. version 0. */
	static final String SCHEMA_VERSION_KEY = "schemaVersion";

	/** Inline doc written above {@link #SCHEMA_VERSION_KEY}, same {@code _comment_} idiom as a field. */
	static final String SCHEMA_VERSION_DOC =
			"Layout version of this file. Written by the mod - do not raise it by hand. An older file is"
					+ " migrated automatically on load; a file from a NEWER mod version is refused (the"
					+ " server logs a warning and runs on built-in defaults instead of guessing).";

	/**
	 * Json key of the machine-written block that records, per knob, the built-in default this file was
	 * last written against (MOD-553, ADR-033). It is what turns "the number in the file" into "the number
	 * the operator chose": a value equal to the default recorded beside it was never chosen by anybody,
	 * so a later build is free to replace it with ITS default.
	 *
	 * <p>One flat block rather than a sibling key next to every knob, on purpose. The operator's own
	 * editing area — the sections — keeps exactly the shape it had, the bookkeeping is quarantined in
	 * one clearly-labelled place with one explanation, and the block is keyed by knob name alone, so a
	 * future migration that moves a knob between sections does not have to move its recorded default.
	 */
	static final String BUILTIN_DEFAULTS_KEY = "builtinDefaults";

	/**
	 * Inline doc written above {@link #BUILTIN_DEFAULTS_KEY}. It has to state the limitation, because
	 * the limitation is invisible from the data: see {@link #migrateAddBuiltinDefaults}.
	 */
	static final String BUILTIN_DEFAULTS_DOC =
			"Written by the mod - do not edit. For each knob, the mod's own default at the moment this"
					+ " file was last saved. On load, a knob still holding exactly that number counts as"
					+ " untouched, so a mod update that changes the default applies it here too (the"
					+ " change is logged). A knob you edited to anything else is left alone forever."
					+ " NOTE: a value you deliberately set to the same number as the default cannot be"
					+ " told apart from one you never touched, and will follow the default when it"
					+ " changes. NOTE: for files created before this block existed, the reference point"
					+ " is the moment it was added - defaults that drifted before that are not"
					+ " recoverable.";

	/**
	 * One rung of the schema ladder: "a document stored at {@code fromVersion} becomes a document at
	 * {@code fromVersion + 1} after {@code apply} has rewritten it in place". Kept as data rather than a
	 * chain of {@code if}s so adding the next hop is one list entry — see {@link #VERSION}.
	 */
	private record Migration(int fromVersion, Consumer<JsonObject> apply) {
	}

	private final KnobRegistry registry;

	/**
	 * The ladder, ascending, one entry per version hop ({@code migrations.get(i).fromVersion() == i},
	 * {@code migrations.size() == VERSION}). Pinned by {@code ConfigSchemaTest}.
	 */
	private final List<Migration> migrations;

	ConfigSchema(KnobRegistry registry) {
		this.registry = registry;
		this.migrations = List.of(
				new Migration(0, this::migrateFlatToSections),
				new Migration(1, this::migrateAddBuiltinDefaults));
	}

	/** {@code fromVersion} of each rung of the ladder, in ladder order. */
	public List<Integer> migrationFromVersions() {
		return migrations.stream().map(Migration::fromVersion).toList();
	}

	/**
	 * Layout version recorded in {@code file}. Absent means "written before MOD-402", i.e. the flat
	 * layout, which is version 0 — the one case where a missing key is information rather than a
	 * default. A present-but-not-a-number value throws, exactly like any other wrong-type key.
	 */
	int readVersion(JsonObject file) {
		JsonElement e = file.get(SCHEMA_VERSION_KEY);
		if (e == null || e.isJsonNull()) {
			return 0;
		}
		if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
			return e.getAsInt();
		}
		throw new IllegalArgumentException("config key '" + SCHEMA_VERSION_KEY + "' must be a number, got " + e);
	}

	/**
	 * Walk {@code file} up the ladder from {@code fileVersion} to {@link #VERSION}, rewriting it in
	 * place. Called before staging, so the rest of the load only ever sees a current-shape document.
	 * Nothing is written back here — what lands on disk is the canonical form, rebuilt from the live
	 * values by the self-heal step of {@link ConfigFile#loadFrom}.
	 */
	void migrate(JsonObject file, int fileVersion, Path path) {
		if (fileVersion >= VERSION) {
			return;
		}
		for (Migration step : migrations) {
			if (step.fromVersion() >= fileVersion) {
				step.apply().accept(file);
			}
		}
		Industrialization.LOGGER.info("[config] migrated {} from schemaVersion {} to {}",
				path, fileVersion, VERSION);
	}

	/**
	 * v0 → v1: the pre-MOD-402 flat file, where all knobs sat at the top level, becomes the sectioned
	 * one. Every registered key found at the root is moved into its declared section, so a server that
	 * has been running since before MOD-402 keeps every value it had edited.
	 *
	 * <p>This step is load-bearing, not decorative: the loader stages each field from its section object
	 * only. Remove this migration and every old install silently reverts to defaults.
	 */
	private void migrateFlatToSections(JsonObject file) {
		for (ConfigField field : registry.fields()) {
			JsonElement value = file.remove(field.key);
			if (value == null) {
				continue;
			}
			JsonElement existing = file.get(field.section.id);
			JsonObject body;
			if (existing != null && existing.isJsonObject()) {
				body = existing.getAsJsonObject();
			} else {
				body = new JsonObject();
				file.add(field.section.id, body);
			}
			body.add(field.key, value);
		}
	}

	/**
	 * v1 → v2: give an existing file the {@link #BUILTIN_DEFAULTS_KEY} block it never had, filled with
	 * the defaults compiled into THIS build.
	 *
	 * <p><b>The limitation this step creates, stated plainly</b> (ADR-033, point 2). For a file written
	 * before the block existed, the default each value was stored against was never recorded; the only
	 * honest reference point left is the moment of migration. From here on a knob equal to this build's
	 * default counts as untouched, and a knob holding anything else counts as operator-edited and is
	 * preserved forever — the drift accumulated before this step is <b>not recoverable</b>. This is also
	 * said in {@link #BUILTIN_DEFAULTS_DOC} and in {@code docs/SERVER_CONFIG.md}. Recording each knob's
	 * CURRENT file value instead was rejected: it would mark every knob untouched, and the next default
	 * change would silently overwrite real operator edits.
	 *
	 * <p><b>No test can observe this step's output, and that is expected.</b> A block filled with
	 * exactly the compiled defaults can never trigger an adoption on the load that installs it, and the
	 * self-heal rewrites the block anyway. The step keeps {@link #migrate}'s postcondition honest and the
	 * ladder at one rung per hop; do not "simplify" it away on the grounds that nothing goes red.
	 */
	private void migrateAddBuiltinDefaults(JsonObject file) {
		JsonObject defaults = new JsonObject();
		for (ConfigField field : registry.fields()) {
			field.writeDefault(defaults);
		}
		file.add(BUILTIN_DEFAULTS_KEY, defaults);
	}
}

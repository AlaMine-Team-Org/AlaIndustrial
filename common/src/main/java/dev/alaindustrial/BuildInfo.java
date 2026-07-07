package dev.alaindustrial;

import com.google.gson.JsonObject;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.util.GsonHelper;

/**
 * Runtime-readable build stamp (MOD-022: loader-neutral, in {@code common}). Reads
 * {@code /alaindustrial.build.json} (shipped in the common resource set, so both loaders bake it) from the
 * classpath once and caches it. If the resource is missing or unreadable (e.g. running from a raw source
 * set), falls back to {@code "unknown"}/{@code "dev"} so the {@code /ala} command never blanks out.
 */
public final class BuildInfo {
	private BuildInfo() {
	}

	private static volatile boolean loaded;
	private static String version;
	private static String git;
	private static String built;

	/** Mod version string. Never empty. */
	public static String version() {
		ensureLoaded();
		return version;
	}

	/** Short git commit hash, or "dev" when no build stamp was baked. */
	public static String git() {
		ensureLoaded();
		return git;
	}

	/** Build timestamp string, or "unknown" when no build stamp was baked. */
	public static String built() {
		ensureLoaded();
		return built;
	}

	private static void ensureLoaded() {
		if (loaded) {
			return;
		}
		synchronized (BuildInfo.class) {
			if (loaded) {
				return;
			}
			load();
			loaded = true;
		}
	}

	private static void load() {
		try (InputStream in = BuildInfo.class.getResourceAsStream("/alaindustrial.build.json")) {
			if (in != null) {
				JsonObject o = GsonHelper.parse(new InputStreamReader(in, StandardCharsets.UTF_8));
				version = GsonHelper.getAsString(o, "version", "unknown");
				git = GsonHelper.getAsString(o, "git", "dev");
				built = GsonHelper.getAsString(o, "built", "unknown");
				if (version.isEmpty()) {
					version = "unknown";
				}
				return;
			}
		} catch (Exception e) {
			Industrialization.LOGGER.warn("[buildinfo] failed to read build stamp: {}", e.toString());
		}
		version = "unknown";
		git = "dev";
		built = "unknown";
	}
}

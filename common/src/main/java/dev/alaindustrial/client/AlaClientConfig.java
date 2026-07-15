package dev.alaindustrial.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.alaindustrial.Industrialization;
import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.util.GsonHelper;

/** Client-only visual and convenience settings, stored separately from server balance config. */
public final class AlaClientConfig {
	private static final int DEFAULT_NETWORK_COLOR = 0xFF11577A;
	private static final int[] NETWORK_COLOR_PRESETS = {
			0xFF11577A, 0xFF22C55E, 0xFFF59E0B, 0xFF38BDF8, 0xFFE879F9, 0xFFE5E7EB
	};

	public static boolean networkOverlayEnabled = true;
	public static boolean networkOverlayThroughBlocks = true;
	public static boolean networkOverlayFlowDots = true;
	public static int networkOverlayColor = DEFAULT_NETWORK_COLOR;
	public static int networkOverlayAlpha = 255;
	public static boolean alwaysDetailedTooltips = false;
	public static boolean showEuNumbers = true;
	/** Worn-pack charge readout (MOD-065). On by default; toggled in-game with the H key. */
	public static boolean energyHudEnabled = true;
	/** Held-drill charge readout (MOD-079). On by default; toggled in-game with its own key (default J),
	 * independent of the pack readout so each can be bound and shown separately. */
	public static boolean drillHudEnabled = true;

	private static Path path;

	private AlaClientConfig() {
	}

	public static void init(Path configDir) {
		path = configDir.resolve("alaindustrial-client.json");
		load();
	}

	public static Snapshot snapshot() {
		return new Snapshot(networkOverlayEnabled, networkOverlayThroughBlocks, networkOverlayFlowDots,
				networkOverlayColor, networkOverlayAlpha, alwaysDetailedTooltips, showEuNumbers, energyHudEnabled,
				drillHudEnabled);
	}

	public static void apply(Snapshot snapshot) {
		networkOverlayEnabled = snapshot.networkOverlayEnabled();
		networkOverlayThroughBlocks = snapshot.networkOverlayThroughBlocks();
		networkOverlayFlowDots = snapshot.networkOverlayFlowDots();
		networkOverlayColor = withAlpha(snapshot.networkOverlayColor(), snapshot.networkOverlayAlpha());
		networkOverlayAlpha = clamp(snapshot.networkOverlayAlpha(), 0, 255);
		alwaysDetailedTooltips = snapshot.alwaysDetailedTooltips();
		showEuNumbers = snapshot.showEuNumbers();
		energyHudEnabled = snapshot.energyHudEnabled();
		drillHudEnabled = snapshot.drillHudEnabled();
		save();
	}

	public static void load() {
		if (path == null) {
			return;
		}
		try {
			if (Files.exists(path)) {
				try (BufferedReader reader = Files.newBufferedReader(path)) {
					JsonObject o = GsonHelper.parse(reader);
					networkOverlayEnabled = GsonHelper.getAsBoolean(o, "networkOverlayEnabled", networkOverlayEnabled);
					networkOverlayThroughBlocks = GsonHelper.getAsBoolean(o, "networkOverlayThroughBlocks",
							networkOverlayThroughBlocks);
					networkOverlayFlowDots = GsonHelper.getAsBoolean(o, "networkOverlayFlowDots", networkOverlayFlowDots);
					networkOverlayColor = parseColor(o, "networkOverlayColor", networkOverlayColor);
					networkOverlayAlpha = clamp(GsonHelper.getAsInt(o, "networkOverlayAlpha", networkOverlayAlpha), 0, 255);
					networkOverlayColor = withAlpha(networkOverlayColor, networkOverlayAlpha);
					alwaysDetailedTooltips = GsonHelper.getAsBoolean(o, "alwaysDetailedTooltips", alwaysDetailedTooltips);
					showEuNumbers = GsonHelper.getAsBoolean(o, "showEuNumbers", showEuNumbers);
					energyHudEnabled = GsonHelper.getAsBoolean(o, "energyHudEnabled", energyHudEnabled);
					drillHudEnabled = GsonHelper.getAsBoolean(o, "drillHudEnabled", drillHudEnabled);
				}
				Industrialization.LOGGER.info("[client-config] loaded {}", path);
			} else {
				save();
				Industrialization.LOGGER.info("[client-config] wrote defaults to {}", path);
			}
		} catch (Exception e) {
			Industrialization.LOGGER.error("[client-config] failed to load {}: {}", path, e.toString());
		}
	}

	private static void save() {
		if (path == null) {
			return;
		}
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(toJson(snapshot())));
		} catch (Exception e) {
			Industrialization.LOGGER.error("[client-config] failed to save {}: {}", path, e.toString());
		}
	}

	private static JsonObject toJson(Snapshot snapshot) {
		JsonObject o = new JsonObject();
		o.addProperty("networkOverlayEnabled", snapshot.networkOverlayEnabled());
		o.addProperty("networkOverlayThroughBlocks", snapshot.networkOverlayThroughBlocks());
		o.addProperty("networkOverlayFlowDots", snapshot.networkOverlayFlowDots());
		o.addProperty("networkOverlayColor", colorString(snapshot.networkOverlayColor()));
		o.addProperty("networkOverlayAlpha", snapshot.networkOverlayAlpha());
		o.addProperty("alwaysDetailedTooltips", snapshot.alwaysDetailedTooltips());
		o.addProperty("showEuNumbers", snapshot.showEuNumbers());
		o.addProperty("energyHudEnabled", snapshot.energyHudEnabled());
		o.addProperty("drillHudEnabled", snapshot.drillHudEnabled());
		return o;
	}

	private static int parseColor(JsonObject o, String key, int fallback) {
		if (!o.has(key)) {
			return fallback;
		}
		try {
			String raw = GsonHelper.getAsString(o, key);
			String hex = raw.startsWith("#") ? raw.substring(1) : raw;
			if (hex.length() == 6) {
				return 0xFF000000 | Integer.parseUnsignedInt(hex, 16);
			}
			if (hex.length() == 8) {
				return (int) Long.parseLong(hex, 16);
			}
		} catch (Exception ignored) {
			Industrialization.LOGGER.warn("[client-config] invalid color for {} in {}, using default", key, path);
		}
		return fallback;
	}

	private static String colorString(int color) {
		return String.format(java.util.Locale.ROOT, "#%06X", color & 0x00FFFFFF);
	}

	private static int withAlpha(int color, int alpha) {
		return (clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF);
	}

	static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	static int nextNetworkColor(int color) {
		int rgb = color & 0x00FFFFFF;
		for (int i = 0; i < NETWORK_COLOR_PRESETS.length; i++) {
			if ((NETWORK_COLOR_PRESETS[i] & 0x00FFFFFF) == rgb) {
				return NETWORK_COLOR_PRESETS[(i + 1) % NETWORK_COLOR_PRESETS.length];
			}
		}
		return NETWORK_COLOR_PRESETS[0];
	}

	public record Snapshot(
			boolean networkOverlayEnabled,
			boolean networkOverlayThroughBlocks,
			boolean networkOverlayFlowDots,
			int networkOverlayColor,
			int networkOverlayAlpha,
			boolean alwaysDetailedTooltips,
			boolean showEuNumbers,
			boolean energyHudEnabled,
			boolean drillHudEnabled) {
		public static Snapshot defaults() {
			return new Snapshot(true, true, true, DEFAULT_NETWORK_COLOR, 255, false, true, true, true);
		}

		public Snapshot withNetworkOverlayEnabled(boolean value) {
			return new Snapshot(value, networkOverlayThroughBlocks, networkOverlayFlowDots, networkOverlayColor,
					networkOverlayAlpha, alwaysDetailedTooltips, showEuNumbers, energyHudEnabled, drillHudEnabled);
		}

		public Snapshot withNetworkOverlayThroughBlocks(boolean value) {
			return new Snapshot(networkOverlayEnabled, value, networkOverlayFlowDots, networkOverlayColor,
					networkOverlayAlpha, alwaysDetailedTooltips, showEuNumbers, energyHudEnabled, drillHudEnabled);
		}

		public Snapshot withNetworkOverlayFlowDots(boolean value) {
			return new Snapshot(networkOverlayEnabled, networkOverlayThroughBlocks, value, networkOverlayColor,
					networkOverlayAlpha, alwaysDetailedTooltips, showEuNumbers, energyHudEnabled, drillHudEnabled);
		}

		public Snapshot withNetworkOverlayColor(int value) {
			return new Snapshot(networkOverlayEnabled, networkOverlayThroughBlocks, networkOverlayFlowDots, value,
					networkOverlayAlpha, alwaysDetailedTooltips, showEuNumbers, energyHudEnabled, drillHudEnabled);
		}

		public Snapshot withNetworkOverlayAlpha(int value) {
			return new Snapshot(networkOverlayEnabled, networkOverlayThroughBlocks, networkOverlayFlowDots,
					networkOverlayColor, clamp(value, 0, 255), alwaysDetailedTooltips, showEuNumbers, energyHudEnabled,
					drillHudEnabled);
		}

		public Snapshot withAlwaysDetailedTooltips(boolean value) {
			return new Snapshot(networkOverlayEnabled, networkOverlayThroughBlocks, networkOverlayFlowDots,
					networkOverlayColor, networkOverlayAlpha, value, showEuNumbers, energyHudEnabled, drillHudEnabled);
		}

		public Snapshot withShowEuNumbers(boolean value) {
			return new Snapshot(networkOverlayEnabled, networkOverlayThroughBlocks, networkOverlayFlowDots,
					networkOverlayColor, networkOverlayAlpha, alwaysDetailedTooltips, value, energyHudEnabled,
					drillHudEnabled);
		}

		public Snapshot withEnergyHudEnabled(boolean value) {
			return new Snapshot(networkOverlayEnabled, networkOverlayThroughBlocks, networkOverlayFlowDots,
					networkOverlayColor, networkOverlayAlpha, alwaysDetailedTooltips, showEuNumbers, value,
					drillHudEnabled);
		}

		public Snapshot withDrillHudEnabled(boolean value) {
			return new Snapshot(networkOverlayEnabled, networkOverlayThroughBlocks, networkOverlayFlowDots,
					networkOverlayColor, networkOverlayAlpha, alwaysDetailedTooltips, showEuNumbers, energyHudEnabled,
					value);
		}
	}
}

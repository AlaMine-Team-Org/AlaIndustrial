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
	/**
	 * Default start of the analyzer trace's colour ramp (MOD-665): a vivid blue, so the blue-to-yellow
	 * trace reads as blue at a producer — the previous dark teal came out grey through the translucent
	 * sheath.
	 */
	private static final int DEFAULT_NETWORK_COLOR = 0xFF3B82F6;
	/** The default before MOD-665; an old file still holding it moves to {@link #DEFAULT_NETWORK_COLOR}. */
	static final int OBSOLETE_DEFAULT_NETWORK_COLOR = 0xFF11577A;
	private static final int[] NETWORK_COLOR_PRESETS = {
			0xFF3B82F6, 0xFF22C55E, 0xFFF59E0B, 0xFF38BDF8, 0xFFE879F9, 0xFFE5E7EB
	};

	/**
	 * Default opacity of the analyzer overlay (MOD-665): translucent, because the trace is always drawn
	 * through walls now and an opaque one hides the very cables it follows.
	 */
	public static final int DEFAULT_NETWORK_ALPHA = 191;
	/**
	 * The key of the removed "through blocks" switch (MOD-665, D8). It is no longer read or written; its
	 * presence marks a file saved by an older version, see {@link #load()}.
	 */
	public static final String OBSOLETE_THROUGH_BLOCKS_KEY = "networkOverlayThroughBlocks";

	public static boolean networkOverlayEnabled = true;
	public static boolean networkOverlayFlowDots = true;
	public static int networkOverlayColor = (DEFAULT_NETWORK_ALPHA << 24) | (DEFAULT_NETWORK_COLOR & 0x00FFFFFF);
	public static int networkOverlayAlpha = DEFAULT_NETWORK_ALPHA;
	public static boolean alwaysDetailedTooltips = false;
	public static boolean showEuNumbers = true;
	/** Worn-pack charge readout (MOD-065). On by default; toggled in-game with the H key. */
	public static boolean energyHudEnabled = true;
	/** Held-drill charge readout (MOD-079). On by default; toggled in-game with its own key (default J),
	 * independent of the pack readout so each can be bound and shown separately. */
	public static boolean drillHudEnabled = true;
	/**
	 * How far the world is dimmed behind a root inspection, in percent (MOD-584). The wash is what
	 * makes the underground shape readable against a lit surface, so it cannot be 0: a fully
	 * transparent wash does not composite as "no change", it composites as a black screen.
	 * 85 leaves the terrain, horizon and sky faintly readable — enough to keep your bearings.
	 */
	public static int rootInspectionDim = 85;

	/**
	 * Whether the Mirror Concentrator's assembly schematic is drawn (MOD-603). On by default: it is
	 * how a player finds out the machine can be grown at all, and it only appears while the crosshair
	 * is on the machine itself.
	 */
	public static boolean concentratorSchematicEnabled = true;
	/** Dragged offset of the upgrade panel from its docked position (MOD-080). Persisted between sessions. */
	public static int upgradePanelDX = 0;
	public static int upgradePanelDY = 0;
	/** Dragged offset of the statistics panel from its docked position (MOD-125). Persisted between sessions. */
	public static int statsPanelDX = 0;
	public static int statsPanelDY = 0;

	private static Path path;

	private AlaClientConfig() {
	}

	public static void init(Path configDir) {
		path = configDir.resolve("alaindustrial-client.json");
		load();
	}

	public static Snapshot snapshot() {
		return new Snapshot(networkOverlayEnabled, networkOverlayFlowDots,
				networkOverlayColor, networkOverlayAlpha, alwaysDetailedTooltips, showEuNumbers, energyHudEnabled,
				drillHudEnabled);
	}

	public static void apply(Snapshot snapshot) {
		networkOverlayEnabled = snapshot.networkOverlayEnabled();
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
					networkOverlayFlowDots = GsonHelper.getAsBoolean(o, "networkOverlayFlowDots", networkOverlayFlowDots);
					networkOverlayColor = parseColor(o, "networkOverlayColor", networkOverlayColor);
					networkOverlayAlpha = clamp(GsonHelper.getAsInt(o, "networkOverlayAlpha", networkOverlayAlpha), 0, 255);
					// MOD-665: a file from before the "through blocks" switch was removed holds 255, the old
					// default, in nearly every case — the old version wrote its defaults out on first start.
					// The trace is always see-through now and meant to be translucent, so that one value moves
					// to the new default; any other opacity the player chose is kept.
					if (o.has(OBSOLETE_THROUGH_BLOCKS_KEY) && networkOverlayAlpha == 255) {
						networkOverlayAlpha = DEFAULT_NETWORK_ALPHA;
					}
					// The same holds for the colour: the old default moves to the new one, a colour the player
					// picked stays.
					if (o.has(OBSOLETE_THROUGH_BLOCKS_KEY)
							&& (networkOverlayColor & 0x00FFFFFF) == (OBSOLETE_DEFAULT_NETWORK_COLOR & 0x00FFFFFF)) {
						networkOverlayColor = DEFAULT_NETWORK_COLOR;
					}
					networkOverlayColor = withAlpha(networkOverlayColor, networkOverlayAlpha);
					alwaysDetailedTooltips = GsonHelper.getAsBoolean(o, "alwaysDetailedTooltips", alwaysDetailedTooltips);
					showEuNumbers = GsonHelper.getAsBoolean(o, "showEuNumbers", showEuNumbers);
					energyHudEnabled = GsonHelper.getAsBoolean(o, "energyHudEnabled", energyHudEnabled);
					drillHudEnabled = GsonHelper.getAsBoolean(o, "drillHudEnabled", drillHudEnabled);
					rootInspectionDim = clamp(GsonHelper.getAsInt(o, "rootInspectionDim", rootInspectionDim), 10, 95);
					concentratorSchematicEnabled = GsonHelper.getAsBoolean(o, "concentratorSchematicEnabled",
							concentratorSchematicEnabled);
					upgradePanelDX = GsonHelper.getAsInt(o, "upgradePanelDX", upgradePanelDX);
					upgradePanelDY = GsonHelper.getAsInt(o, "upgradePanelDY", upgradePanelDY);
					statsPanelDX = GsonHelper.getAsInt(o, "statsPanelDX", statsPanelDX);
					statsPanelDY = GsonHelper.getAsInt(o, "statsPanelDY", statsPanelDY);
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
		o.addProperty("networkOverlayFlowDots", snapshot.networkOverlayFlowDots());
		o.addProperty("networkOverlayColor", colorString(snapshot.networkOverlayColor()));
		o.addProperty("networkOverlayAlpha", snapshot.networkOverlayAlpha());
		o.addProperty("alwaysDetailedTooltips", snapshot.alwaysDetailedTooltips());
		o.addProperty("showEuNumbers", snapshot.showEuNumbers());
		o.addProperty("energyHudEnabled", snapshot.energyHudEnabled());
		o.addProperty("drillHudEnabled", snapshot.drillHudEnabled());
		o.addProperty("rootInspectionDim", rootInspectionDim);
		o.addProperty("concentratorSchematicEnabled", concentratorSchematicEnabled);
		o.addProperty("upgradePanelDX", upgradePanelDX);
		o.addProperty("upgradePanelDY", upgradePanelDY);
		o.addProperty("statsPanelDX", statsPanelDX);
		o.addProperty("statsPanelDY", statsPanelDY);
		return o;
	}

	/** Persist the dragged upgrade-panel offset (MOD-080). Called on drag release, not every frame. */
	public static void savePanelPosition(int dx, int dy) {
		upgradePanelDX = dx;
		upgradePanelDY = dy;
		save();
	}

	/** Persist the dragged statistics-panel offset (MOD-125). Called on drag release, not every frame. */
	public static void saveStatsPanelPosition(int dx, int dy) {
		statsPanelDX = dx;
		statsPanelDY = dy;
		save();
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

	public static int nextNetworkColor(int color) {
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
			boolean networkOverlayFlowDots,
			int networkOverlayColor,
			int networkOverlayAlpha,
			boolean alwaysDetailedTooltips,
			boolean showEuNumbers,
			boolean energyHudEnabled,
			boolean drillHudEnabled) {
		public static Snapshot defaults() {
			return new Snapshot(true, true, DEFAULT_NETWORK_COLOR, DEFAULT_NETWORK_ALPHA, false, true, true, true);
		}

		public Snapshot withNetworkOverlayEnabled(boolean value) {
			return new Snapshot(value, networkOverlayFlowDots, networkOverlayColor,
					networkOverlayAlpha, alwaysDetailedTooltips, showEuNumbers, energyHudEnabled, drillHudEnabled);
		}


		public Snapshot withNetworkOverlayFlowDots(boolean value) {
			return new Snapshot(networkOverlayEnabled, value, networkOverlayColor,
					networkOverlayAlpha, alwaysDetailedTooltips, showEuNumbers, energyHudEnabled, drillHudEnabled);
		}

		public Snapshot withNetworkOverlayColor(int value) {
			return new Snapshot(networkOverlayEnabled, networkOverlayFlowDots, value,
					networkOverlayAlpha, alwaysDetailedTooltips, showEuNumbers, energyHudEnabled, drillHudEnabled);
		}

		public Snapshot withNetworkOverlayAlpha(int value) {
			return new Snapshot(networkOverlayEnabled, networkOverlayFlowDots,
					networkOverlayColor, clamp(value, 0, 255), alwaysDetailedTooltips, showEuNumbers, energyHudEnabled,
					drillHudEnabled);
		}

		public Snapshot withAlwaysDetailedTooltips(boolean value) {
			return new Snapshot(networkOverlayEnabled, networkOverlayFlowDots,
					networkOverlayColor, networkOverlayAlpha, value, showEuNumbers, energyHudEnabled, drillHudEnabled);
		}

		public Snapshot withShowEuNumbers(boolean value) {
			return new Snapshot(networkOverlayEnabled, networkOverlayFlowDots,
					networkOverlayColor, networkOverlayAlpha, alwaysDetailedTooltips, value, energyHudEnabled,
					drillHudEnabled);
		}

		public Snapshot withEnergyHudEnabled(boolean value) {
			return new Snapshot(networkOverlayEnabled, networkOverlayFlowDots,
					networkOverlayColor, networkOverlayAlpha, alwaysDetailedTooltips, showEuNumbers, value,
					drillHudEnabled);
		}

		public Snapshot withDrillHudEnabled(boolean value) {
			return new Snapshot(networkOverlayEnabled, networkOverlayFlowDots,
					networkOverlayColor, networkOverlayAlpha, alwaysDetailedTooltips, showEuNumbers, energyHudEnabled,
					value);
		}
	}
}

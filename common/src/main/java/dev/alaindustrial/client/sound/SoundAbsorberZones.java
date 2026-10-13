package dev.alaindustrial.client.sound;

import dev.alaindustrial.block.SoundAbsorberBlock;
import dev.alaindustrial.core.acoustic.AcousticZones;
import dev.alaindustrial.core.acoustic.AcousticZones.Cell;
import dev.alaindustrial.core.acoustic.AcousticZones.Zone;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jspecify.annotations.Nullable;

/**
 * The client's view of the sound absorbers around the player (MOD-804): where the groups are and how much a
 * sound at a given point is muffled. Nothing here comes over the network — the absorbers' block states are the
 * whole protocol (their seams and {@link SoundAbsorberBlock#TIER} are written by the server), so the registry
 * finds the blocks itself, from the loaded chunks.
 *
 * <p><b>Cheap when there is nothing.</b> The scan runs at most once a second (and when the player has moved
 * far or changed level), lazily from whoever asks first, and asks each 16³ section around the player whether
 * its palette can hold an absorber at all ({@link LevelChunkSection#maybeHas}) — a world without absorbers
 * pays a few hundred palette checks a second and every sound query returns at the first {@code isEmpty()}.
 *
 * <p>Only what a player hears is touched: positional (non-relative) sounds of {@link SoundSource#BLOCKS} and
 * {@link SoundSource#AMBIENT}; never music, UI, voices, players, mobs, weather, the reactor siren or the
 * Geiger counter ({@link #UNMUFFLABLE}). The mod's own machine hum is muffled by
 * {@link MachineHumSoundInstance} itself, so it can keep its audible floor; {@link #factorFor} skips it.
 *
 * <p>Client thread only (sound plays, sound ticks, HUD and frame hooks all run there). Reset on disconnect
 * ({@code ClientDisconnectReset}).
 */
public final class SoundAbsorberZones {
	private SoundAbsorberZones() {
	}

	/** Chunks scanned on each side of the player. */
	private static final int SCAN_CHUNKS = 3;
	/** Sections scanned above and below the player's. */
	private static final int SCAN_SECTIONS = 3;
	/** Rescan interval in game ticks. */
	private static final int SCAN_EVERY_TICKS = 20;
	/** A move this far (squared, blocks) since the last scan forces a new one: a teleport. */
	private static final double RESCAN_MOVE_SQR = 16.0 * 16.0;

	/**
	 * Sound events that are never muffled, whatever their category (owner decision 2026-10-13): the reactor
	 * siren is a warning, and the Geiger counter is how a player measures danger.
	 */
	private static final Set<Identifier> UNMUFFLABLE = Set.of(
			Identifier.fromNamespaceAndPath("alaindustrial", "reactor_alarm"),
			Identifier.fromNamespaceAndPath("alaindustrial", "geiger_click"));

	private static final Predicate<BlockState> IS_ABSORBER = state -> state.getBlock() instanceof SoundAbsorberBlock;

	private static @Nullable ClientLevel seenLevel;
	private static long lastScan = Long.MIN_VALUE;
	private static double scanX;
	private static double scanY;
	private static double scanZ;
	private static List<Zone> zones = List.of();
	private static Map<Long, Zone> byCell = Map.of();

	/**
	 * The volume multiplier for a sound about to play or playing now: 1 for anything the absorber leaves
	 * alone (see the class comment), otherwise the strongest zone at the sound's position.
	 */
	public static float factorFor(SoundInstance sound) {
		if (sound.isRelative() || sound instanceof MachineHumSoundInstance) {
			return 1.0f;
		}
		SoundSource source = sound.getSource();
		if (source != SoundSource.BLOCKS && source != SoundSource.AMBIENT) {
			return 1.0f;
		}
		if (UNMUFFLABLE.contains(sound.getIdentifier())) {
			return 1.0f;
		}
		return factorAt(sound.getX(), sound.getY(), sound.getZ());
	}

	/** The volume multiplier at a point: the strongest zone containing it, or 1. */
	public static float factorAt(double x, double y, double z) {
		refreshIfDue();
		if (zones.isEmpty()) {
			return 1.0f;
		}
		return AcousticZones.factorAt(zones, x, y, z);
	}

	/** The groups found around the player (for the wrench overlay). */
	public static List<Zone> zones() {
		refreshIfDue();
		return zones;
	}

	/** The group the absorber at {@code pos} belongs to, or null when none was found there. */
	public static @Nullable Zone groupAt(BlockPos pos) {
		refreshIfDue();
		return byCell.get(pos.asLong());
	}

	/** Forget everything (disconnect). */
	public static void reset() {
		seenLevel = null;
		lastScan = Long.MIN_VALUE;
		zones = List.of();
		byCell = Map.of();
	}

	private static void refreshIfDue() {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null || mc.player == null) {
			if (seenLevel != null) {
				reset();
			}
			return;
		}
		long now = level.getGameTime();
		double px = mc.player.getX();
		double py = mc.player.getY();
		double pz = mc.player.getZ();
		double dx = px - scanX;
		double dy = py - scanY;
		double dz = pz - scanZ;
		boolean due = level != seenLevel || lastScan == Long.MIN_VALUE || now < lastScan
				|| now - lastScan >= SCAN_EVERY_TICKS || dx * dx + dy * dy + dz * dz > RESCAN_MOVE_SQR;
		if (!due) {
			return;
		}
		seenLevel = level;
		lastScan = now;
		scanX = px;
		scanY = py;
		scanZ = pz;
		scan(level, BlockPos.containing(px, py, pz));
	}

	private static void scan(ClientLevel level, BlockPos centre) {
		List<Cell> cells = new ArrayList<>();
		int centreChunkX = SectionPos.blockToSectionCoord(centre.getX());
		int centreChunkZ = SectionPos.blockToSectionCoord(centre.getZ());
		int centreSectionY = SectionPos.blockToSectionCoord(centre.getY());
		for (int cx = centreChunkX - SCAN_CHUNKS; cx <= centreChunkX + SCAN_CHUNKS; cx++) {
			for (int cz = centreChunkZ - SCAN_CHUNKS; cz <= centreChunkZ + SCAN_CHUNKS; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
				if (chunk == null || chunk instanceof EmptyLevelChunk) {
					continue;
				}
				LevelChunkSection[] sections = chunk.getSections();
				for (int sy = centreSectionY - SCAN_SECTIONS; sy <= centreSectionY + SCAN_SECTIONS; sy++) {
					int index = level.getSectionIndexFromSectionY(sy);
					if (index < 0 || index >= sections.length) {
						continue;
					}
					LevelChunkSection section = sections[index];
					if (section.hasOnlyAir() || !section.maybeHas(IS_ABSORBER)) {
						continue;
					}
					int baseX = cx << 4;
					int baseY = sy << 4;
					int baseZ = cz << 4;
					for (int y = 0; y < 16; y++) {
						for (int z = 0; z < 16; z++) {
							for (int x = 0; x < 16; x++) {
								if (IS_ABSORBER.test(section.getBlockState(x, y, z))) {
									cells.add(new Cell(baseX + x, baseY + y, baseZ + z));
								}
							}
						}
					}
				}
			}
		}
		if (cells.isEmpty()) {
			zones = List.of();
			byCell = Map.of();
			return;
		}
		Map<Cell, Zone> members = AcousticZones.membership(cells);
		Map<Long, Zone> index = new HashMap<>();
		for (Map.Entry<Cell, Zone> entry : members.entrySet()) {
			Cell cell = entry.getKey();
			index.put(BlockPos.asLong(cell.x(), cell.y(), cell.z()), entry.getValue());
		}
		zones = List.copyOf(new LinkedHashSet<>(members.values()));
		byCell = index;
	}
}

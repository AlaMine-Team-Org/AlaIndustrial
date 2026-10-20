package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.compat.LineStructures;
import dev.alaindustrial.core.guide.ArchiveRecord;
import dev.alaindustrial.registry.ModDataRegistries;
import dev.alaindustrial.settlement.record.RecordNumber;
import dev.alaindustrial.settlement.shift.ShiftWorkerBiography;
import dev.alaindustrial.settlement.shift.ShiftWorkerBiographyFragment;
import dev.alaindustrial.settlement.shift.TownFacts;
import dev.alaindustrial.worldgen.AbandonedLabPlacer;
import dev.alaindustrial.worldgen.town.TownBiographyFacts;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * L2 suite for the shift worker's biography (MOD-854, epic decision R26a): the fragments are datapack data on both
 * loaders, a biography reads the same every time, and the digits it quotes are those on the walls of a town placed in
 * the world.
 */
public final class ShiftWorkerBiographyScenarios {

	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(ShiftWorkerBiographyScenarios::fragmentsAreData, "shift_worker_biography_is_data"),
				RosterEntry.of(ShiftWorkerBiographyScenarios::quotedDigitsAreOnTheWalls,
						"shift_worker_biography_quotes_the_town").ticks(200, 200));

		private Roster() {}
	}

	private static final Identifier PLAINS = Industrialization.id("ala_town_plains");
	/** Away from the towns of {@code AlaTownScenarios} (chunks 5000/5000 and 5000/5012). */
	private static final ChunkPos TOWN_CHUNK = new ChunkPos(5000, 5036);

	private ShiftWorkerBiographyScenarios() {
	}

	/**
	 * The mod's fragments load from data — ten origins and twelve of every other slot — and a biography computed
	 * twice is the same four lines, one per slot, in the form of the worker's body.
	 *
	 * @implements MOD-854-BIO-DATA — the biography fragments are datapack data; a biography is stable
	 */
	public static void fragmentsAreData(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Registry<ShiftWorkerBiographyFragment> fragments = level.registryAccess()
				.lookupOrThrow(ModDataRegistries.SHIFT_WORKER_BIOGRAPHY);
		Map<ShiftWorkerBiographyFragment.Slot, Integer> perSlot =
				new EnumMap<>(ShiftWorkerBiographyFragment.Slot.class);
		fragments.forEach(fragment -> perSlot.merge(fragment.slot(), 1, Integer::sum));
		helper.assertTrue(perSlot.equals(Map.of(ShiftWorkerBiographyFragment.Slot.ORIGIN, 10,
				ShiftWorkerBiographyFragment.Slot.OCCUPATION, 12, ShiftWorkerBiographyFragment.Slot.HABIT, 12,
				ShiftWorkerBiographyFragment.Slot.MEMORY, 12)), "fragments per slot: " + perSlot);
		for (int index = 0; index < 50; index++) {
			RecordNumber number = number(index * 397);
			boolean slim = index % 2 == 0;
			List<Component> first = ShiftWorkerBiography.lines(level.registryAccess(), level.getSeed(), number, slim,
					TownFacts.UNKNOWN);
			List<Component> again = ShiftWorkerBiography.lines(level.registryAccess(), level.getSeed(), number, slim,
					TownFacts.UNKNOWN);
			helper.assertTrue(first.equals(again), number + ": the biography changed between two reads");
			helper.assertTrue(first.size() == 4, number + ": " + first.size() + " lines");
			for (Component line : first) {
				String key = ((TranslatableContents) line.getContents()).getKey();
				helper.assertTrue(key.endsWith(slim ? ".female" : ".male"), number + ": the line " + key
						+ " is not in the " + (slim ? "female" : "male") + " form");
			}
		}
		helper.succeed();
	}

	/**
	 * A plains town placed in the world: its facts name the lab's number and exactly the numbers its whole house
	 * plaques show, and every digit a biography quotes for that town is among them.
	 *
	 * @implements MOD-854-BIO-TOWN — the plaque and lab numbers a biography quotes are this town's real ones
	 */
	public static void quotedDigitsAreOnTheWalls(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ChunkGenerator generator = AlaTownSite.generator(level, Biomes.PLAINS);
		StructureStart start = AlaTownSite.generate(level, AlaTownSite.structure(level, PLAINS), generator,
				TOWN_CHUNK, AlaTownScenarios.SEED);
		helper.assertTrue(start.isValid(), "no plains town on flat ground");
		AlaTownSite.place(level, start, generator, AlaTownScenarios.SEED);
		TownFacts facts = TownBiographyFacts.of(level.getSeed(), LineStructures.templates(level), start);
		int lab = AlaTownSite.labs(start).getFirst().labNumber(level.getSeed());
		helper.assertTrue(facts.lab().isPresent() && facts.lab().getAsInt() == lab,
				"facts name the lab " + facts.lab() + ", the plaque under the hall says " + lab);
		Set<Integer> onTheWalls = new TreeSet<>();
		for (PoolElementStructurePiece piece : AlaTownSite.poolPieces(start)) {
			List<BlockPos> plates = AlaTownScenarios.plates(level, piece.getBoundingBox());
			if (plates.isEmpty()) {
				continue;
			}
			AbandonedLabPlacer.sortForReader(plates,
					level.getBlockState(plates.getFirst()).getValue(HorizontalDirectionalBlock.FACING));
			List<Integer> digits = AlaTownScenarios.digitsOf(level, plates);
			if (digits.size() == 3) {
				onTheWalls.add(digits.get(0) * 100 + digits.get(1) * 10 + digits.get(2));
			}
		}
		helper.assertTrue(!onTheWalls.isEmpty(), "no whole plaque in the town");
		helper.assertTrue(new TreeSet<>(facts.plaques()).equals(onTheWalls),
				"facts name the plaques " + facts.plaques() + ", the walls show " + onTheWalls);
		String labText = String.format(Locale.ROOT, "%03d", lab);
		int quoting = 0;
		for (int index = 0; index < 2000; index++) {
			for (Component line : ShiftWorkerBiography.lines(level.registryAccess(), level.getSeed(), number(index),
					false, facts)) {
				TranslatableContents contents = (TranslatableContents) line.getContents();
				for (Object arg : contents.getArgs()) {
					String text = String.valueOf(arg);
					boolean real = text.length() == 3 ? onTheWalls.contains(Integer.parseInt(text))
							: labText.contains(text);
					helper.assertTrue(real, contents.getKey() + " quotes " + text + ", not a number of this town");
					quoting++;
				}
			}
		}
		helper.assertTrue(quoting > 0, "no biography of 2000 quoted the town");
		helper.succeed();
	}

	/** The record number at {@code index} of the space, read back through its text — the public way to get one. */
	private static RecordNumber number(int index) {
		int digits = index % 1000;
		String text = ArchiveRecord.ALPHABET.charAt(index / 1000 % ArchiveRecord.ALPHABET.length()) + "-"
				+ (char) ('0' + digits / 100) + (char) ('0' + digits / 10 % 10) + (char) ('0' + digits % 10);
		return RecordNumber.parse(text).orElseThrow();
	}
}

package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.SaveFormatTestSupport.parse;

import com.mojang.serialization.Codec;
import dev.alaindustrial.core.GuideBookState;
import dev.alaindustrial.core.WelcomeMessageState;
import dev.alaindustrial.teleporter.TeleporterRegistry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.Level;

/**
 * L2 round trips of the mod's world-level saved data (MOD-701, batch 0): the guide-book ledger, the
 * welcome flag and the teleporter station registry. Until this class nothing read these codecs back
 * — the scenarios that use them check behaviour inside one server run, where the data never leaves
 * memory.
 *
 * <p>Each case starts from a hand-written tag in the shape the file on disk has, decodes it, checks
 * the values a player would miss, and then requires encode -> decode -> encode to give the same tag.
 * The first tag is not compared with the re-encoded one directly: the codecs leave out a field that
 * holds its default, so a hand-written tag may legitimately spell out more than a re-save does.
 *
 * <p>The two collections behind these codecs are hash-based, so the order of a saved list is not
 * part of the format; lists are compared as sets.
 */
public final class SavedDataRoundTripScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(SavedDataRoundTripScenarios::guideBookLedgerRoundTrips,
								"save_format_guide_book_ledger_round_trips")
						.fabricId("SaveFormatGameTest", "guideBookLedgerRoundTrips").ticks(20, 40),
				RosterEntry.of(SavedDataRoundTripScenarios::welcomeFlagRoundTrips,
								"save_format_welcome_flag_round_trips")
						.fabricId("SaveFormatGameTest", "welcomeFlagRoundTrips").ticks(20, 40),
				RosterEntry.of(SavedDataRoundTripScenarios::teleporterRegistryRoundTrips,
								"save_format_teleporter_registry_round_trips")
						.fabricId("SaveFormatGameTest", "teleporterRegistryRoundTrips").ticks(20, 40));

		private Roster() {}
	}

	private SavedDataRoundTripScenarios() {}

	private static final UUID ALICE = UUIDUtil.uuidFromIntArray(new int[] {1, 2, 3, 4});
	private static final UUID BOB = UUIDUtil.uuidFromIntArray(new int[] {5, 6, 7, 8});
	private static final UUID CAROL = UUIDUtil.uuidFromIntArray(new int[] {9, 10, 11, 12});

	/**
	 * @implements R-PER-01 — the guide-book ledger keeps every player it has given a book to.
	 * @covers R-PER-01
	 */
	public static void guideBookLedgerRoundTrips(GameTestHelper helper) {
		RegistryOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		GuideBookState state = GuideBookState.CODEC.parse(ops,
				parse("{given:[[I;1,2,3,4],[I;5,6,7,8]]}")).getOrThrow();
		if (!state.hasReceived(ALICE) || !state.hasReceived(BOB) || state.hasReceived(CAROL)) {
			helper.fail("guide-book ledger did not come back: alice=" + state.hasReceived(ALICE)
					+ " bob=" + state.hasReceived(BOB) + " carol=" + state.hasReceived(CAROL));
			return;
		}
		stable(helper, "guide-book ledger", GuideBookState.CODEC, state, ops);
	}

	/**
	 * @implements R-PER-01 — a world that has already greeted its players stays greeted.
	 * @covers R-PER-01
	 */
	public static void welcomeFlagRoundTrips(GameTestHelper helper) {
		RegistryOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		WelcomeMessageState state = WelcomeMessageState.CODEC.parse(ops, parse("{greeted:1b}")).getOrThrow();
		if (state.claim()) {
			helper.fail("a saved greeted:1b came back as not greeted — the welcome would be sent twice");
			return;
		}
		stable(helper, "welcome flag", WelcomeMessageState.CODEC, state, ops);
	}

	/**
	 * @implements R-PER-01 — the teleporter station registry keeps each station's owner, privacy,
	 *     chip, formed flag, energy and timestamp, across dimensions.
	 * @covers R-PER-01
	 */
	public static void teleporterRegistryRoundTrips(GameTestHelper helper) {
		RegistryOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		TeleporterRegistry registry = TeleporterRegistry.CODEC.parse(ops, parse("{stations:["
				+ "{dimension:\"minecraft:overworld\",pos:[I;10,64,-20],owner:[I;1,2,3,4],private:0b,chip:1b,"
				+ "formed:1b,energy:48000L,updated:1234L},"
				+ "{dimension:\"minecraft:the_nether\",pos:[I;-5,70,3],private:1b,chip:0b,formed:0b,"
				+ "energy:0L,updated:99L}]}")).getOrThrow();
		TeleporterRegistry.Entry home = new TeleporterRegistry.Entry(Optional.of(ALICE), false, true, true,
				48000L, 1234L);
		TeleporterRegistry.Entry nether = new TeleporterRegistry.Entry(Optional.empty(), true, false, false,
				0L, 99L);
		Optional<TeleporterRegistry.Entry> gotHome = registry.find(Level.OVERWORLD, new BlockPos(10, 64, -20));
		Optional<TeleporterRegistry.Entry> gotNether = registry.find(Level.NETHER, new BlockPos(-5, 70, 3));
		if (!gotHome.equals(Optional.of(home)) || !gotNether.equals(Optional.of(nether))) {
			helper.fail("teleporter registry did not come back: overworld=" + gotHome + " nether=" + gotNether);
			return;
		}
		stable(helper, "teleporter registry", TeleporterRegistry.CODEC, registry, ops);
	}

	/** encode -> decode -> encode gives the same tag (lists compared as sets); succeeds or fails the test. */
	private static <T> void stable(GameTestHelper helper, String what, Codec<T> codec, T value, RegistryOps<Tag> ops) {
		Tag first = codec.encodeStart(ops, value).getOrThrow();
		Tag second = codec.encodeStart(ops, codec.parse(ops, first).getOrThrow()).getOrThrow();
		if (!unordered(first).equals(unordered(second))) {
			helper.fail(what + " is not stable through a save: " + first + " re-encodes as " + second);
			return;
		}
		helper.succeed();
	}

	/** {@code tag} with every list directly under it replaced by the set of its elements. */
	private static Object unordered(Tag tag) {
		if (!(tag instanceof CompoundTag compound)) {
			return tag;
		}
		Map<String, Object> out = new HashMap<>();
		for (String key : compound.keySet()) {
			Tag value = compound.get(key);
			out.put(key, value instanceof ListTag list ? new HashSet<>(list) : value);
		}
		return out;
	}
}

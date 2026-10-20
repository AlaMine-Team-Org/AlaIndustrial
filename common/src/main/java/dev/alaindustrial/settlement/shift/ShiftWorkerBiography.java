package dev.alaindustrial.settlement.shift;

import dev.alaindustrial.registry.ModDataRegistries;
import dev.alaindustrial.settlement.record.RecordNumber;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * A shift worker's biography — the «personal file» of its card (MOD-854, epic decisions R26a and R72): four lines in
 * the dry voice of an archive extract, one per {@link ShiftWorkerBiographyFragment.Slot}, chosen from the datapack
 * registry {@code alaindustrial:shift_worker_biography} by the world seed and the worker's number
 * ({@link BiographyPick}).
 *
 * <p><b>Two ways to use it.</b>
 * <ul>
 *   <li>{@link #lines(RegistryAccess, long, RecordNumber, boolean, TownFacts)} — computed on the spot, nothing stored:
 *       the same number in the same world reads the same lines between sessions and for every player.</li>
 *   <li>{@link #choose} at issue, the four ids kept in the worker's record, {@link #render} when the card is opened —
 *       the way ADR-050 point 7 describes: it lets a town avoid repeating a memory ({@code avoidMemories}), and a
 *       datapack edit never rewrites a biography already given. A kept id that has gone from the datapack, or whose
 *       town fact is no longer known, is chosen again for its slot alone.</li>
 * </ul>
 *
 * <p><b>Where to call it.</b> On the server: the registry is not synced to the client. The card's payload carries the
 * returned components; they are translatable, so each client reads them in its own language.
 *
 * <pre>{@code
 * TownFacts facts = TownBiographyFacts.at(level, settlement.corePos());   // once per town, then cache it
 * List<Component> lines = ShiftWorkerBiography.lines(level.registryAccess(), level.getSeed(), record.number(),
 *         look.slim(), facts);
 * }</pre>
 *
 * <p><b>The text.</b> Each line is {@code Component.translatable(<key>.male | <key>.female, args…)}, the form by the
 * worker's body ({@code slim} — the woman's form, decision R3g); {@code args} are the town's digits as plain strings
 * (the substitutions {@code plaque}, {@code lab}, … of the data are {@code %1$s}, {@code %2$s} … of the lang files).
 */
public final class ShiftWorkerBiography {

	private ShiftWorkerBiography() {
	}

	/**
	 * The biography of the worker with {@code number} in the world with {@code worldSeed}, from the fragments of
	 * {@code access}: four lines with the mod's data, fewer only when a datapack left a slot empty, none when the
	 * registry is missing.
	 *
	 * @param slim  whether the worker has the slim body — the text is then in its female form
	 * @param facts what the worker's town lets the lines quote; {@link TownFacts#UNKNOWN} — nothing
	 */
	public static List<Component> lines(RegistryAccess access, long worldSeed, RecordNumber number, boolean slim,
			TownFacts facts) {
		return registry(access)
				.map(fragments -> render(fragments, choose(fragments, worldSeed, number, facts, Set.of()), worldSeed,
						number, slim, facts))
				.orElse(List.of());
	}

	/**
	 * The ids of the fragments of the worker's biography, in slot order — what a record keeps. Memories in
	 * {@code avoidMemories} (those already given in the town) are skipped while another memory fits.
	 */
	public static List<Identifier> choose(Registry<ShiftWorkerBiographyFragment> fragments, long worldSeed,
			RecordNumber number, TownFacts facts, Set<Identifier> avoidMemories) {
		Set<String> avoid = avoidMemories.stream().map(Identifier::toString).collect(Collectors.toSet());
		return BiographyPick.pick(candidates(fragments), worldSeed, number.index(), facts, avoid).stream()
				.map(line -> Identifier.parse(line.id()))
				.toList();
	}

	/**
	 * The lines of the fragments {@code chosen} (from {@link #choose}, possibly read back from a save). A slot whose
	 * fragment has gone from {@code fragments} or cannot be filled from {@code facts} is chosen again; a slot with no
	 * kept id is filled the same way.
	 */
	public static List<Component> render(Registry<ShiftWorkerBiographyFragment> fragments, List<Identifier> chosen,
			long worldSeed, RecordNumber number, boolean slim, TownFacts facts) {
		List<BiographyPick.Candidate> candidates = candidates(fragments);
		List<BiographyPick.Line> fresh = null;
		List<Component> lines = new ArrayList<>(BiographyPick.SLOTS);
		for (int slot = 0; slot < BiographyPick.SLOTS; slot++) {
			Optional<BiographyPick.Candidate> kept = keptFor(slot, chosen, candidates, facts);
			Optional<BiographyPick.Line> line;
			if (kept.isPresent()) {
				line = Optional.of(new BiographyPick.Line(kept.get().id(),
						BiographyPick.args(kept.get(), worldSeed, number.index(), facts)));
			} else {
				if (fresh == null) {
					fresh = BiographyPick.pick(candidates, worldSeed, number.index(), facts, Set.of());
				}
				line = lineOfSlot(slot, fresh, fragments);
			}
			line.ifPresent(found -> lines.add(Component.translatable(
					fragments.getValue(Identifier.parse(found.id())).textKey(slim), found.args().toArray())));
		}
		return List.copyOf(lines);
	}

	private static Optional<BiographyPick.Candidate> keptFor(int slot, List<Identifier> chosen,
			List<BiographyPick.Candidate> candidates, TownFacts facts) {
		for (Identifier id : chosen) {
			for (BiographyPick.Candidate candidate : candidates) {
				if (candidate.slot() == slot && candidate.id().equals(id.toString())
						&& BiographyPick.canFill(candidate, facts)) {
					return Optional.of(candidate);
				}
			}
		}
		return Optional.empty();
	}

	private static Optional<BiographyPick.Line> lineOfSlot(int slot, List<BiographyPick.Line> lines,
			Registry<ShiftWorkerBiographyFragment> fragments) {
		for (BiographyPick.Line line : lines) {
			if (fragments.getValue(Identifier.parse(line.id())).slot().ordinal() == slot) {
				return Optional.of(line);
			}
		}
		return Optional.empty();
	}

	private static Optional<Registry<ShiftWorkerBiographyFragment>> registry(RegistryAccess access) {
		return access.lookup(ModDataRegistries.SHIFT_WORKER_BIOGRAPHY);
	}

	private static List<BiographyPick.Candidate> candidates(Registry<ShiftWorkerBiographyFragment> fragments) {
		List<BiographyPick.Candidate> candidates = new ArrayList<>();
		for (Map.Entry<ResourceKey<ShiftWorkerBiographyFragment>, ShiftWorkerBiographyFragment> entry
				: fragments.entrySet()) {
			ShiftWorkerBiographyFragment fragment = entry.getValue();
			candidates.add(new BiographyPick.Candidate(entry.getKey().identifier().toString(),
					fragment.slot().ordinal(), fragment.weight(), fragment.tags(), fragment.forbid(),
					fragment.substitutions().stream()
							.map(ShiftWorkerBiographyFragment.Substitution::getSerializedName).toList()));
		}
		return candidates;
	}
}

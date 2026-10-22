package dev.alaindustrial.settlement.works;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;

/**
 * One building of an ALA town as its town remembers it (MOD-857, epic decision R79): where the structure put it,
 * which template it is and which it is restored to, and what the last full comparison with that template found.
 * Immutable — {@link TownWorks} stores a new one in place of the old.
 *
 * <p>The state is the last answer of {@link BuildingStateRules}, not a fact of its own: the world is the source of
 * truth, and the town's scanner compares it again and again ({@link TownWorksScanner}).
 *
 * @param template       the ruin's template, the one the town's structure placed (its address, ADR-050 §4)
 * @param intact         the template it is restored to (MOD-856), or empty for a building with no intact version
 * @param name           the translation key of the building's name, from its definition ({@link TownBuildingDef})
 * @param origin         where the structure placed the template: the template's (0, 0, 0) in the world
 * @param rotation       the template's rotation
 * @param state          its state after the last pass, or after the last change of its jobs
 * @param matched        cells that matched the intact template on the last full pass
 * @param total          cells the comparison counts; 0 — never measured
 * @param everRestored   it has been restored at least once (the town hall's log has its line)
 * @param allowedMissing the cells it may miss and still be intact ({@link BuildingStateRules.Before}); -1 before its
 *                       first restoration
 * @param missingKeys    the translation keys of the key points the last pass found missing
 */
public record TownBuilding(Identifier template, Optional<Identifier> intact, String name, BlockPos origin,
		Rotation rotation, BuildingState state, int matched, int total, boolean everRestored, int allowedMissing,
		List<String> missingKeys) {

	public TownBuilding {
		origin = origin.immutable();
		missingKeys = List.copyOf(missingKeys);
	}

	/** A building just found in its town's structure: a ruin nobody measured yet. */
	public static TownBuilding found(Identifier template, Optional<Identifier> intact, String name, BlockPos origin,
			Rotation rotation) {
		return new TownBuilding(template, intact, name, origin, rotation, BuildingState.RUIN, 0, 0, false, -1,
				List.of());
	}

	/** Whether a full pass has measured it. */
	public boolean measured() {
		return total > 0;
	}

	/** The matching share of the last pass in whole percent, or -1 before the first. */
	public int percent() {
		return measured() ? (int) ((long) matched * 100 / total) : -1;
	}

	/** What the rules need to know about its past. */
	public BuildingStateRules.Before before() {
		return new BuildingStateRules.Before(everRestored, allowedMissing);
	}

	TownBuilding withVerdict(BuildingStateRules.Measure measure, BuildingStateRules.Verdict verdict) {
		return new TownBuilding(template, intact, name, origin, rotation, verdict.state(), measure.matched(),
				measure.total(), verdict.everRestored(), verdict.allowedMissing(), measure.missingKeys());
	}

	TownBuilding withState(BuildingState newState) {
		return new TownBuilding(template, intact, name, origin, rotation, newState, matched, total, everRestored,
				allowedMissing, missingKeys);
	}

	TownBuilding withDefinition(Optional<Identifier> newIntact, String newName) {
		return new TownBuilding(template, newIntact, newName, origin, rotation, state, matched, total, everRestored,
				allowedMissing, missingKeys);
	}

	private static final Codec<BuildingState> STATE_CODEC =
			Codec.STRING.xmap(BuildingState::byName, BuildingState::serializedName);

	public static final Codec<TownBuilding> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.fieldOf("template").forGetter(TownBuilding::template),
			Identifier.CODEC.optionalFieldOf("intact").forGetter(TownBuilding::intact),
			Codec.STRING.optionalFieldOf("name", "").forGetter(TownBuilding::name),
			BlockPos.CODEC.fieldOf("origin").forGetter(TownBuilding::origin),
			Rotation.CODEC.optionalFieldOf("rotation", Rotation.NONE).forGetter(TownBuilding::rotation),
			STATE_CODEC.optionalFieldOf("state", BuildingState.RUIN).forGetter(TownBuilding::state),
			Codec.INT.optionalFieldOf("matched", 0).forGetter(TownBuilding::matched),
			Codec.INT.optionalFieldOf("total", 0).forGetter(TownBuilding::total),
			Codec.BOOL.optionalFieldOf("ever_restored", false).forGetter(TownBuilding::everRestored),
			Codec.INT.optionalFieldOf("allowed_missing", -1).forGetter(TownBuilding::allowedMissing),
			Codec.STRING.listOf().optionalFieldOf("missing_keys", List.of()).forGetter(TownBuilding::missingKeys)
	).apply(i, TownBuilding::new));
}

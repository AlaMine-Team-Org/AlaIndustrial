package dev.alaindustrial.settlement.works;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alaindustrial.registry.ModDataRegistries;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A building of an ALA town as data (MOD-857, ADR-050 §4): which structure template is the building, which template
 * it is restored to, what it is called, and what «restored» means for it (epic decision R86). A datapack registry,
 * {@code data/<ns>/alaindustrial/town_building/<style>/<piece>.json}, read only on the server; the built-in buildings
 * are files in the same format, so a datapack adds or retunes a building without code.
 *
 * <p><b>A piece of a town is a building when a definition names its template</b> ({@code template}). The ground pieces
 * between the buildings and the lab under the town hall have none, so the town's building list skips them. That is
 * the address of a building: the template of its structure piece, mapped through this registry — no marker block in
 * the hand-made ruins (ADR-050 §4).
 *
 * <p><b>Key points</b> are named groups of the intact template's cells that must all stand for the building to count
 * as restored: a block list (ids, or {@code #tags}), a box in the template's own coordinates (before the piece's
 * rotation), or both — the cells of the intact template inside the box whose block is on the list; air cells never
 * count. The town hall's are the core, the capsule, the roof over the hall, the door and the drone pad.
 *
 * @param template   the ruin's template, the one a town's structure places
 * @param intact     the template it is restored to (MOD-856), or empty for a building with no intact version yet
 * @param name       the translation key of the building's name
 * @param restoredAt the share of matching cells «restored» needs, 0..1 (R86: 0.9)
 * @param keyPoints  the groups of cells that must all stand
 * @param stages     cells put into a build stage by hand (MOD-860), first match wins; a cell no entry covers is staged
 *                   by its block ({@link BuildMaterials})
 * @param clearRing  how wide a ring around the building's footprint a clearing frees of terrain, from the layer over
 *                   its floor up (owner's round 4 of stage 6); 0 — none
 * @param dispatcherSpot the cell of the intact template, in its own coordinates, kept for the town's dispatcher
 *                   (MOD-891, R158): the plan wants air there, and a dispatcher standing there counts as in place;
 *                   empty for a building with no such cell
 */
public record TownBuildingDef(Identifier template, Optional<Identifier> intact, String name, double restoredAt,
		List<KeyPoint> keyPoints, List<StageOverride> stages, int clearRing, Optional<BlockPos> dispatcherSpot) {

	/** Most key points one building may name. */
	public static final int MAX_KEY_POINTS = 16;
	/** The ring a clearing frees around a building whose definition names none. */
	public static final int DEFAULT_CLEAR_RING = 1;
	/** The widest ring a definition may ask for. */
	public static final int MAX_CLEAR_RING = 3;

	public TownBuildingDef {
		keyPoints = List.copyOf(keyPoints.subList(0, Math.min(MAX_KEY_POINTS, keyPoints.size())));
		stages = List.copyOf(stages);
		restoredAt = Math.max(0.0, Math.min(1.0, restoredAt));
		clearRing = Math.max(0, Math.min(MAX_CLEAR_RING, clearRing));
	}

	/**
	 * One key point.
	 *
	 * @param name   the translation key of its name, shown while it is missing
	 * @param blocks the blocks it is made of — ids or {@code #tags}; empty: any block
	 * @param from   one corner of its box in the template's coordinates; empty with {@code to}: no box
	 * @param to     the other corner
	 */
	public record KeyPoint(String name, List<String> blocks, Optional<BlockPos> from, Optional<BlockPos> to) {

		public KeyPoint {
			blocks = List.copyOf(blocks);
		}

		/** Whether the intact template's cell at {@code local} holding {@code state} belongs to this key point. */
		public boolean covers(BlockPos local, BlockState state) {
			if (state.isAir()) {
				return false;
			}
			if (from.isPresent() && to.isPresent()) {
				BlockPos a = from.get();
				BlockPos b = to.get();
				if (local.getX() < Math.min(a.getX(), b.getX()) || local.getX() > Math.max(a.getX(), b.getX())
						|| local.getY() < Math.min(a.getY(), b.getY()) || local.getY() > Math.max(a.getY(), b.getY())
						|| local.getZ() < Math.min(a.getZ(), b.getZ()) || local.getZ() > Math.max(a.getZ(), b.getZ())) {
					return false;
				}
			}
			if (blocks.isEmpty()) {
				return true;
			}
			Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
			for (String entry : blocks) {
				if (entry.startsWith("#")) {
					Identifier tag = Identifier.tryParse(entry.substring(1));
					if (tag != null && state.is(TagKey.create(Registries.BLOCK, tag))) {
						return true;
					}
				} else if (id.toString().equals(entry) || id.equals(Identifier.tryParse(entry))) {
					return true;
				}
			}
			return false;
		}

		public static final Codec<KeyPoint> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("name").forGetter(KeyPoint::name),
				Codec.STRING.listOf().optionalFieldOf("blocks", List.of()).forGetter(KeyPoint::blocks),
				BlockPos.CODEC.optionalFieldOf("from").forGetter(KeyPoint::from),
				BlockPos.CODEC.optionalFieldOf("to").forGetter(KeyPoint::to)
		).apply(i, KeyPoint::new));
	}

	/**
	 * A datapack's say on which build stage some cells of the intact template belong to (MOD-860, R28): the cells a
	 * key-point-like selector covers — a block list, a box in the template's coordinates, or both — go into
	 * {@code stage}. A stage name this build does not know leaves the entry out.
	 *
	 * @param stage  the stage's name ({@link BuildStage#serializedName()})
	 * @param blocks the blocks it covers — ids or {@code #tags}; empty: any block
	 * @param from   one corner of its box in the template's coordinates; empty with {@code to}: no box
	 * @param to     the other corner
	 */
	public record StageOverride(String stage, List<String> blocks, Optional<BlockPos> from, Optional<BlockPos> to) {

		public StageOverride {
			blocks = List.copyOf(blocks);
		}

		/** The stage it names, if this build knows it. */
		public Optional<BuildStage> buildStage() {
			return BuildStage.byName(stage);
		}

		/** Whether the intact template's cell at {@code local} holding {@code state} is one of its cells. */
		public boolean covers(BlockPos local, BlockState state) {
			return new KeyPoint(stage, blocks, from, to).covers(local, state);
		}

		public static final Codec<StageOverride> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("stage").forGetter(StageOverride::stage),
				Codec.STRING.listOf().optionalFieldOf("blocks", List.of()).forGetter(StageOverride::blocks),
				BlockPos.CODEC.optionalFieldOf("from").forGetter(StageOverride::from),
				BlockPos.CODEC.optionalFieldOf("to").forGetter(StageOverride::to)
		).apply(i, StageOverride::new));
	}

	public static final Codec<TownBuildingDef> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.fieldOf("template").forGetter(TownBuildingDef::template),
			Identifier.CODEC.optionalFieldOf("intact").forGetter(TownBuildingDef::intact),
			Codec.STRING.fieldOf("name").forGetter(TownBuildingDef::name),
			Codec.DOUBLE.optionalFieldOf("restored_at", BuildingStateRules.DEFAULT_RESTORED_AT)
					.forGetter(TownBuildingDef::restoredAt),
			KeyPoint.CODEC.listOf().optionalFieldOf("key_points", List.of()).forGetter(TownBuildingDef::keyPoints),
			StageOverride.CODEC.listOf().optionalFieldOf("stages", List.of()).forGetter(TownBuildingDef::stages),
			Codec.INT.optionalFieldOf("clear_ring", DEFAULT_CLEAR_RING).forGetter(TownBuildingDef::clearRing),
			BlockPos.CODEC.optionalFieldOf("dispatcher_spot").forGetter(TownBuildingDef::dispatcherSpot)
	).apply(i, TownBuildingDef::new));

	/** The definitions of {@code access}'s {@code town_building} registry, by the ruin template each names. */
	public static Map<Identifier, TownBuildingDef> byTemplate(RegistryAccess access) {
		Map<Identifier, TownBuildingDef> out = new HashMap<>();
		access.lookup(ModDataRegistries.TOWN_BUILDING)
				.ifPresent(registry -> registry.forEach(def -> out.putIfAbsent(def.template(), def)));
		return out;
	}
}

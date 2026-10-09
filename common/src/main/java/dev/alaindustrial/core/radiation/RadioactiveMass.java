package dev.alaindustrial.core.radiation;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.core.radiation.RadiationSources.Source;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;

/**
 * Uranium compacted into blocks still radiates like the ingots inside it (MOD-796).
 *
 * <p><b>An item counts as many ingots as went into it.</b> A radioactive tag says how strongly ONE item
 * radiates; a block of uranium is nine ingots, and counting it as one would make compaction the cheapest
 * shield in the game. The weight is a tag rather than a number on the item so a datapack can weigh its own
 * blocks: {@link #NINE_INGOTS} for the block, its stairs and its wall (the stonecutter makes each from one
 * block), {@link #FIVE_INGOTS} for the slab (half a block, rounded up). An item in neither tag weighs one.
 *
 * <p><b>A placed block is a source in the world</b>, found the way irradiated soil is: the section palette
 * answers "could this section hold one" before any cell is read. The blocks around a player are emitted as
 * ONE source at their centre, and it leaks no more than one placed container may
 * ({@code radiationContainerMaxItems} × the high dose) — a uranium wall is a hazard to walk past, not a
 * death sentence from across the room.
 */
public final class RadioactiveMass {
	/** Items worth nine ingots: the uranium block, its stairs and its wall. */
	public static final TagKey<Item> NINE_INGOTS =
			TagKey.create(Registries.ITEM, Industrialization.id("radioactive_mass/nine_ingots"));
	/** Items worth five ingots: the uranium slab. */
	public static final TagKey<Item> FIVE_INGOTS =
			TagKey.create(Registries.ITEM, Industrialization.id("radioactive_mass/five_ingots"));
	/** Placed blocks that radiate like the uranium ingots they are made of. */
	public static final TagKey<Block> PLACED =
			TagKey.create(Registries.BLOCK, Industrialization.id("radioactive_blocks"));

	private static final Predicate<BlockState> IS_PLACED = state -> state.is(PLACED);

	private RadioactiveMass() {
	}

	/** Ingots this stack's item is worth; one for anything that is not a compacted block. */
	public static int of(ItemStack stack) {
		return stack.is(NINE_INGOTS) ? 9 : stack.is(FIVE_INGOTS) ? 5 : 1;
	}

	/** As above, for a container slot that was never turned into a stack. */
	public static int of(ItemStackTemplate template) {
		return template.typeHolder().is(NINE_INGOTS) ? 9 : template.typeHolder().is(FIVE_INGOTS) ? 5 : 1;
	}

	/** Ingots this placed block is worth: nine, a single slab five, nothing for a block not in {@link #PLACED}. */
	public static int of(BlockState state) {
		if (!state.is(PLACED)) {
			return 0;
		}
		boolean halfSlab = state.getBlock() instanceof SlabBlock && state.getValue(SlabBlock.TYPE) != SlabType.DOUBLE;
		return halfSlab ? 5 : 9;
	}

	/**
	 * Placed uranium blocks within {@code reach} of a point, emitted as one capped source at their centre.
	 *
	 * <p>size-justified: one palette-filtered sweep of the chunk sections, the same shape as
	 * {@code RadiationSources.collectFallout}; split, the loop bounds would travel as six parameters.
	 */
	static void collectPlaced(ServerLevel level, Vec3 centre, int radius, int groundReach, List<Source> out) {
		int cap = RadiationConfig.radiationContainerMaxItems * RadiationConfig.radiationDoseHighPerItem;
		if (groundReach <= 0 || cap <= 0 || RadiationConfig.radiationDoseMediumPerItem <= 0) {
			return;
		}
		int reach = Math.min(radius, groundReach);
		int ingots = 0;
		int counted = 0;
		double sumX = 0;
		double sumY = 0;
		double sumZ = 0;
		int minY = Mth.floor(centre.y) - reach;
		int maxY = Mth.floor(centre.y) + reach;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int cx = SectionPos.blockToSectionCoord(Math.floor(centre.x) - reach);
				cx <= SectionPos.blockToSectionCoord(Math.floor(centre.x) + reach); cx++) {
			for (int cz = SectionPos.blockToSectionCoord(Math.floor(centre.z) - reach);
					cz <= SectionPos.blockToSectionCoord(Math.floor(centre.z) + reach); cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				LevelChunkSection[] sections = chunk.getSections();
				int minSection = Math.max(0, level.getSectionIndex(Math.max(minY, level.getMinY())));
				int maxSection = Math.min(sections.length - 1, level.getSectionIndex(Math.min(maxY, level.getMaxY())));
				for (int index = minSection; index <= maxSection; index++) {
					LevelChunkSection section = sections[index];
					// The palette check: a world with no uranium blocks pays one comparison per section.
					if (section.hasOnlyAir() || !section.maybeHas(IS_PLACED)) {
						continue;
					}
					int baseY = level.getSectionYFromSectionIndex(index) << 4;
					for (int dy = 0; dy < 16; dy++) {
						int y = baseY + dy;
						if (y < minY || y > maxY) {
							continue;
						}
						for (int dx = 0; dx < 16; dx++) {
							for (int dz = 0; dz < 16; dz++) {
								int mass = of(section.getBlockState(dx, dy, dz));
								if (mass == 0) {
									continue;
								}
								Vec3 at = Vec3.atCenterOf(cursor.set((cx << 4) + dx, y, (cz << 4) + dz));
								if (at.distanceTo(centre) > reach + 1) {
									continue;
								}
								ingots += mass;
								counted++;
								sumX += at.x;
								sumY += at.y;
								sumZ += at.z;
							}
						}
					}
				}
			}
		}
		if (counted > 0) {
			int strength = (int) Math.min((long) ingots * RadiationConfig.radiationDoseMediumPerItem, cap);
			out.add(new Source(new Vec3(sumX / counted, sumY / counted, sumZ / counted), strength));
		}
	}
}

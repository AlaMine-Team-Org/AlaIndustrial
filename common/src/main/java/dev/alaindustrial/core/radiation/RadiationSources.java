package dev.alaindustrial.core.radiation;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.FuelRodAssemblyBlockEntity;
import dev.alaindustrial.registry.ModTags;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Where radiation comes from (MOD-470) — asked on behalf of whoever is standing there, player or mob.
 *
 * <p><b>Everything is a point source.</b> A fuelled rack, a pile of uranium on the ground, the stack
 * in somebody's backpack — each is a position and a strength, and every one of them is answered by the
 * same two rules:
 *
 * <ul>
 * <li><b>Line of sight.</b> The shell blocks radiation because it blocks the trace: a casing wall, a
 * closed airlock or a pane of reactor glass has collision, an open doorway does not. From that one
 * rule follow all four cases the design asked for — full dose inside a running sealed room, zero
 * outside it, a leak through an open door and only through it, and a reactor built with no shell
 * irradiating everything around it. No room bookkeeping, no {@code reactorBreachGraceTicks}.</li>
 * <li><b>Distance.</b> Strength falls off with the square of the distance
 * ({@link RadiationCore#attenuate}), so backing away is a real tactic and the far edge of the radius
 * is a trace rather than a death sentence.</li>
 * </ul>
 *
 * <p>The first version applied neither rule to items: uranium dropped inside a sealed room irradiated
 * anyone standing outside the wall, and a rod six blocks away hit exactly as hard as one at your feet.
 * One source model is what keeps those from drifting apart again.
 */
public final class RadiationSources {

	/** One thing that radiates: where it is and how strong it is before distance and walls. */
	public record Source(Vec3 at, int strength) {
	}

	private RadiationSources() {
	}

	/** Everything in the world irradiating this entity where it stands. */
	public static int exposureAt(ServerLevel level, Entity target, int radius) {
		List<Source> sources = new ArrayList<>();
		collectRods(level, target.position(), radius, sources);
		collectGround(level, target.position(), radius, sources);
		return doseFrom(level, target, sources, radius);
	}

	/**
	 * Dose these sources deliver to this entity: attenuated by distance, and dropped entirely when
	 * something solid stands in the way.
	 */
	public static int doseFrom(ServerLevel level, Entity target, List<Source> sources, int radius) {
		if (sources.isEmpty()) {
			return 0;
		}
		Vec3 eyes = target.getEyePosition();
		int dose = 0;
		for (Source source : sources) {
			double distance = source.at().distanceTo(eyes);
			int attenuated = RadiationCore.attenuate(source.strength(), distance, radius);
			if (attenuated <= 0) {
				continue;
			}
			if (hasLineOfSight(level, target, eyes, source.at())) {
				dose += attenuated;
			}
		}
		return dose;
	}

	/**
	 * Fuelled reactor columns near a point.
	 *
	 * <p><b>Read out of the chunks' block-entity maps, not by sweeping blocks.</b> The first version
	 * walked every cell of the (2r+1)³ box and asked the world for a block entity — about 2 200 chunk
	 * lookups per player per second at the shipped radius, for a handful of racks. A chunk knows its own
	 * block entities, and at this radius there are at most four chunks to ask.
	 */
	public static void collectRods(ServerLevel level, Vec3 centre, int radius, List<Source> out) {
		int minChunkX = SectionPos.blockToSectionCoord(Math.floor(centre.x) - radius);
		int maxChunkX = SectionPos.blockToSectionCoord(Math.floor(centre.x) + radius);
		int minChunkZ = SectionPos.blockToSectionCoord(Math.floor(centre.z) - radius);
		int maxChunkZ = SectionPos.blockToSectionCoord(Math.floor(centre.z) + radius);
		for (int cx = minChunkX; cx <= maxChunkX; cx++) {
			for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				for (var entry : chunk.getBlockEntities().entrySet()) {
					BlockEntity be = entry.getValue();
					if (!(be instanceof FuelRodAssemblyBlockEntity rack) || !rack.hasFuel()) {
						continue;
					}
					BlockPos pos = entry.getKey();
					Vec3 at = Vec3.atCenterOf(pos);
					if (at.distanceTo(centre) > radius + 1) {
						continue;
					}
					out.add(new Source(at, Config.radiationRodDosePerTick * rack.getRods()));
				}
			}
		}
	}

	/** Radioactive items lying on the ground nearby — dropping a fuel rod does not switch it off. */
	public static void collectGround(ServerLevel level, Vec3 centre, int radius, List<Source> out) {
		if (Config.radiationGroundRadius <= 0) {
			return;
		}
		double reach = Math.min(radius, Config.radiationGroundRadius);
		AABB box = AABB.ofSize(centre, reach * 2, reach * 2, reach * 2);
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box)) {
			int strength = strengthOf(item.getItem());
			if (strength > 0) {
				// The CENTRE of the item, not its position: an entity's position is the bottom of its box,
				// which sits flush with the floor it rests on, and a shallow trace to that point clips the
				// floor block and reads as "behind a wall" while the pile is in plain sight.
				out.add(new Source(item.getBoundingBox().getCenter(), strength));
			}
		}
	}

	/** Dose per sweep a stack radiates, by the tag it belongs to; containers opened to the set depth. */
	public static int strengthOf(ItemStack stack) {
		return countTagged(stack, ModTags.Items.RADIOACTIVE_LOW, Config.radiationContainerDepth)
						* Config.radiationDoseLowPerItem
				+ countTagged(stack, ModTags.Items.RADIOACTIVE_MEDIUM, Config.radiationContainerDepth)
						* Config.radiationDoseMediumPerItem
				+ countTagged(stack, ModTags.Items.RADIOACTIVE_HIGH, Config.radiationContainerDepth)
						* Config.radiationDoseHighPerItem;
	}

	/** Items of a tag in the player's own inventory, containers opened to the configured depth. */
	public static int carried(Player player, TagKey<Item> tag) {
		int count = 0;
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			count += countTagged(stack, tag, Config.radiationContainerDepth);
		}
		return count;
	}

	/** Items of a tag lying within the ground radius of this entity. */
	public static int countGround(ServerLevel level, Entity entity, TagKey<Item> tag) {
		if (Config.radiationGroundRadius <= 0) {
			return 0;
		}
		AABB box = entity.getBoundingBox().inflate(Config.radiationGroundRadius);
		int count = 0;
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box)) {
			count += countTagged(item.getItem(), tag, Config.radiationContainerDepth);
		}
		return count;
	}

	private static boolean hasLineOfSight(ServerLevel level, Entity viewer, Vec3 from, Vec3 to) {
		BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, viewer));
		return hit.getType() == HitResult.Type.MISS
				|| hit.getBlockPos().equals(BlockPos.containing(to));
	}

	/** Items of a tag in this stack, following nested container contents while depth remains. */
	public static int countTagged(ItemStack stack, TagKey<Item> tag, int depth) {
		if (stack.isEmpty()) {
			return 0;
		}
		int count = stack.is(tag) ? stack.getCount() : 0;
		if (depth > 0) {
			ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
			if (contents != null) {
				for (ItemStackTemplate inner : contents.nonEmptyItems()) {
					count += countTagged(inner, tag, depth - 1);
				}
			}
		}
		return count;
	}

	/**
	 * The template overload: the same question asked of a container slot that was never turned into a
	 * stack. {@code nonEmptyItemCopyStream} would allocate a fresh {@code ItemStack} per slot on every
	 * sweep of every player, and all this needs is the item and the count.
	 */
	public static int countTagged(ItemStackTemplate template, TagKey<Item> tag, int depth) {
		int count = template.typeHolder().is(tag) ? template.count() : 0;
		if (depth > 0) {
			ItemContainerContents contents = template.get(DataComponents.CONTAINER);
			if (contents != null) {
				for (ItemStackTemplate inner : contents.nonEmptyItems()) {
					count += countTagged(inner, tag, depth - 1);
				}
			}
		}
		return count;
	}
}

package dev.alaindustrial.core.radiation;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.IrradiatedSoilBlock;
import dev.alaindustrial.block.entity.AbstractChestBlockEntity;
import dev.alaindustrial.block.entity.FuelRodAssemblyBlockEntity;
import dev.alaindustrial.block.entity.ShieldingChestBlockEntity;
import dev.alaindustrial.item.energy.PouchContents;
import dev.alaindustrial.item.misc.ShieldingPouchItem;
import dev.alaindustrial.loot.PendingLoot;
import dev.alaindustrial.registry.ModDataComponents;
import dev.alaindustrial.registry.ModTags;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
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
		collectFallout(level, target.position(), radius, sources);
		collectContainers(level, target.position(), radius, sources);
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

	/**
	 * Ground poisoned by a reactor accident (MOD-471).
	 *
	 * <p><b>The only BLOCK source in the model, and the reason it can afford to be.</b> Everything else
	 * here is a block entity or an item, both of which a chunk already indexes; ordinary blocks are not
	 * indexed at all, and sweeping the (2r+1)³ box for them is exactly the cost this class was written
	 * to avoid. {@link LevelChunkSection#maybeHas} answers "could this 16³ section contain one" straight
	 * off the palette, so a world with no fallout in it pays a handful of comparisons per sweep and a
	 * crater pays only for the sections the crater is actually in.
	 *
	 * <p>Emitted as ONE source at the centre of the patch rather than one per cell, with the strength
	 * capped by {@code reactorFalloutMaxBlocksCounted}. Per-cell sources would be correct and would also
	 * make a forty-block scar instantly lethal from its far edge — the same trap MOD-474 had to close
	 * for containers, arriving from the other direction.
	 */
	public static void collectFallout(ServerLevel level, Vec3 centre, int radius, List<Source> out) {
		if (!Config.reactorFalloutEnabled || Config.reactorFalloutDosePerBlock <= 0) {
			return;
		}
		int counted = 0;
		int strength = 0;
		double sumX = 0;
		double sumY = 0;
		double sumZ = 0;
		int cap = Config.reactorFalloutMaxBlocksCounted;
		int minChunkX = SectionPos.blockToSectionCoord(Math.floor(centre.x) - radius);
		int maxChunkX = SectionPos.blockToSectionCoord(Math.floor(centre.x) + radius);
		int minChunkZ = SectionPos.blockToSectionCoord(Math.floor(centre.z) - radius);
		int maxChunkZ = SectionPos.blockToSectionCoord(Math.floor(centre.z) + radius);
		int minY = Mth.floor(centre.y) - radius;
		int maxY = Mth.floor(centre.y) + radius;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int cx = minChunkX; cx <= maxChunkX; cx++) {
			for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				int minSection = level.getSectionIndex(Math.max(minY, level.getMinY()));
				int maxSection = level.getSectionIndex(Math.min(maxY, level.getMaxY()));
				for (int index = minSection; index <= maxSection; index++) {
					if (index < 0 || index >= chunk.getSections().length) {
						continue;
					}
					LevelChunkSection section = chunk.getSections()[index];
					// The palette check that makes a block source affordable at all.
					if (section.hasOnlyAir() || !section.maybeHas(IS_FALLOUT)) {
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
								BlockState state = section.getBlockState(dx, dy, dz);
								if (!IS_FALLOUT.test(state)) {
									continue;
								}
								cursor.set((cx << 4) + dx, y, (cz << 4) + dz);
								Vec3 at = Vec3.atCenterOf(cursor);
								if (at.distanceTo(centre) > radius + 1) {
									continue;
								}
								if (cap > 0 && counted >= cap) {
									continue;
								}
								counted++;
								strength += IrradiatedSoilBlock.doseFor(state);
								sumX += at.x;
								sumY += at.y;
								sumZ += at.z;
							}
						}
					}
				}
			}
		}
		if (counted > 0 && strength > 0) {
			out.add(new Source(new Vec3(sumX / counted, sumY / counted, sumZ / counted), strength));
		}
	}

	private static final java.util.function.Predicate<BlockState> IS_FALLOUT =
			state -> state.getBlock() instanceof IrradiatedSoilBlock;

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

	/**
	 * Uranium stored in the containers around this point (MOD-474) — a chest is not a shield.
	 *
	 * <p><b>Why this source had to exist before the shielding chest could.</b> Until now nothing ever
	 * looked inside a block container: rods were read from the chunk, dropped stacks from the entity
	 * list, and carried stacks from the player's own inventory. So a stack of refined uranium was lethal
	 * in your pockets, dangerous on the floor — and completely inert the moment it went into any chest at
	 * all. That made every wooden chest a perfect radiation shield and left the shielding chest with
	 * nothing to be better than. The rule the design wanted ("store it safely or store it dangerously")
	 * needs both halves, and this is the half that was missing.
	 *
	 * <p><b>The list is closed on purpose.</b> Only the containers a player uses to STORE things count:
	 * vanilla chests and barrels, and the mod's own chest tiers. Machine buffers, hoppers and pipes hold
	 * uranium too, but for seconds at a time while a line processes it — making those radiate would turn
	 * every automated refining setup into a no-go zone nobody asked for, and would punish automation for
	 * being automation. Same reasoning, and the same shape, as the closed species list in
	 * {@link RadiationMobs}.
	 *
	 * <p><b>And the shielding chest is the hole in it.</b> {@link ShieldingChestBlockEntity} is skipped —
	 * that single exclusion is the entire mechanic of the block. It is keyed on the block-entity TYPE
	 * rather than on a tag so that no datapack can hand shielding to a barrel and no future chest tier
	 * can inherit it by accident.
	 *
	 * <p>Cost is the same shape as {@link #collectRods}: block entities come out of the chunks' own maps
	 * (at most four chunks at the shipped radius), never from a sweep over the cells of a cube.
	 */
	public static void collectContainers(ServerLevel level, Vec3 centre, int radius, List<Source> out) {
		if (Config.radiationGroundRadius <= 0 || Config.radiationContainerMaxItems <= 0) {
			return;
		}
		double reach = Math.min(radius, Config.radiationGroundRadius);
		int minChunkX = SectionPos.blockToSectionCoord(Math.floor(centre.x) - reach);
		int maxChunkX = SectionPos.blockToSectionCoord(Math.floor(centre.x) + reach);
		int minChunkZ = SectionPos.blockToSectionCoord(Math.floor(centre.z) - reach);
		int maxChunkZ = SectionPos.blockToSectionCoord(Math.floor(centre.z) + reach);
		for (int cx = minChunkX; cx <= maxChunkX; cx++) {
			for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				for (var entry : chunk.getBlockEntities().entrySet()) {
					BlockEntity be = entry.getValue();
					if (!isExposedStorage(be)) {
						continue;
					}
					// A chest whose loot has not been generated yet holds nothing to irradiate, and
					// READING it is what would generate it: getItem unpacks the table with no player
					// in context, which empties another mod's loot chest for good (MOD-524). Skip it
					// and let the player be the one who opens it.
					if (PendingLoot.isPending(be)) {
						continue;
					}
					Vec3 at = Vec3.atCenterOf(entry.getKey());
					if (at.distanceTo(centre) > reach) {
						continue;
					}
					// Capped: a container leaks at most a few items' worth however full it is — see
					// RadiationCore.containerLeak for why an uncapped chest was a trap and not a hazard.
					int strength = RadiationCore.containerLeak(contentsStrength((Container) be),
							Config.radiationContainerMaxItems, Config.radiationDoseHighPerItem);
					if (strength > 0) {
						out.add(new Source(at, strength));
					}
				}
			}
		}
	}

	/** The closed list of containers that do NOT stop radiation — see {@link #collectContainers}. */
	private static boolean isExposedStorage(BlockEntity be) {
		if (be instanceof ShieldingChestBlockEntity) {
			return false;
		}
		return be instanceof AbstractChestBlockEntity
				|| be instanceof ChestBlockEntity
				|| be instanceof BarrelBlockEntity;
	}

	/** Dose per sweep the whole contents of a container radiates. */
	private static int contentsStrength(Container container) {
		int strength = 0;
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (!stack.isEmpty()) {
				strength += strengthOf(stack);
			}
		}
		return strength;
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
		if (depth > 0 && !isShieldingPouch(stack.getItem())) {
			count += taggedInside(stack, tag, depth);
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
		if (depth > 0 && !isShieldingPouch(template.typeHolder().value())) {
			count += taggedInside(template, tag, depth);
		}
		return count;
	}

	/**
	 * The one carried container that stops the radiation of its contents (MOD-545) — the portable
	 * counterpart of {@link #isExposedStorage}'s shielding chest, and keyed the same way, on the item
	 * TYPE rather than on a tag: no datapack can hand shielding to another pouch, and no future pouch
	 * inherits it by accident.
	 */
	private static boolean isShieldingPouch(Item item) {
		return item instanceof ShieldingPouchItem;
	}

	/**
	 * Tagged items inside a carried container, whichever component it keeps them in.
	 *
	 * <p><b>All three are read on purpose (MOD-545).</b> Until then only {@code CONTAINER} was, which
	 * made a shulker box of uranium lethal and a vanilla BUNDLE of the same uranium completely inert —
	 * bundles keep their contents in {@code BUNDLE_CONTENTS}, and the mod's own pouches in
	 * {@code POUCH_CONTENTS}. A bundle of leather and string was therefore a better radiation shield
	 * than a lead chest, for free, and the shielding pouch would have had nothing to be better than.
	 * The exclusion above is the only hole, and it is a decision rather than a blind spot.
	 */
	private static int taggedInside(DataComponentGetter carrier, TagKey<Item> tag, int depth) {
		int count = 0;
		ItemContainerContents contents = carrier.get(DataComponents.CONTAINER);
		if (contents != null) {
			for (ItemStackTemplate inner : contents.nonEmptyItems()) {
				count += countTagged(inner, tag, depth - 1);
			}
		}
		BundleContents bundle = carrier.get(DataComponents.BUNDLE_CONTENTS);
		if (bundle != null) {
			for (ItemStackTemplate inner : bundle.items()) {
				count += countTagged(inner, tag, depth - 1);
			}
		}
		PouchContents pouch = carrier.get(ModDataComponents.POUCH_CONTENTS.get());
		if (pouch != null) {
			for (ItemStack inner : pouch.items()) {
				count += countTagged(inner, tag, depth - 1);
			}
		}
		return count;
	}
}

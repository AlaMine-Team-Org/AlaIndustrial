package dev.alaindustrial.core.waste;

import dev.alaindustrial.registry.ModTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What one item is worth to the Recycler: how much slag mass it carries and which fraction it belongs to
 * (MOD-145).
 *
 * <p><b>Why a chain with a fallback and not a whitelist.</b> The machine is deliberately greedy — it eats
 * anything, the way IndustrialCraft's recycler did — so no hand-kept list can ever cover its input. The
 * rules below run top to bottom and the last one always matches; a modded item nobody has ever seen still
 * gets a verdict. What it does <b>not</b> get is variety: an unrecognised item lands in
 * {@link WasteFraction#OTHER}, which counts toward mass but never toward the grade, so feeding the machine
 * things it cannot identify can never be the optimal play.
 *
 * <p>Pure function of the stack — no level, no block entity, no randomness — so it can be reasoned about
 * and tested on its own.
 */
public final class WasteClassifier {
	/** Anything the chain could not weigh: one unit of mass, no fraction. */
	public static final int MASS_UNKNOWN = 1;
	/** A plain item — dust, seed, fragment. */
	public static final int MASS_ITEM = 2;
	/** A placeable block: the bulk of what a player shovels in. */
	public static final int MASS_BLOCK = 4;
	/** A tool, a weapon, a piece of armour — a whole object, not a handful. */
	public static final int MASS_GEAR = 8;

	private WasteClassifier() {
	}

	/** Mass and fraction of one item of this stack. Never returns {@code null}. */
	public static WasteProfile classify(ItemStack stack) {
		if (stack.isEmpty()) {
			return new WasteProfile(0, WasteFraction.OTHER);
		}
		// 1. Gear first: a diamond pickaxe is metal scrap, not "a tool-shaped block".
		if (isGear(stack)) {
			return new WasteProfile(MASS_GEAR, WasteFraction.METAL);
		}
		// 2. Explicit tags — vanilla's and the common namespace's.
		WasteFraction tagged = byTag(stack);
		if (tagged != null) {
			return new WasteProfile(stack.getItem() instanceof BlockItem ? MASS_BLOCK : MASS_ITEM, tagged);
		}
		// 3. A block we know nothing else about: soils first (the sound test would mistake them for
		//    foliage), then what the block sounds like.
		if (stack.getItem() instanceof BlockItem blockItem) {
			if (isSoil(blockItem.getBlock())) {
				return new WasteProfile(MASS_BLOCK, WasteFraction.MINERAL);
			}
			WasteFraction bySound = bySound(blockItem.getBlock().defaultBlockState());
			return new WasteProfile(MASS_BLOCK, bySound != null ? bySound : WasteFraction.OTHER);
		}
		// 4. Fallback: it burns down to something, but we cannot say to what.
		return new WasteProfile(MASS_UNKNOWN, WasteFraction.OTHER);
	}

	/** A damageable object — tool, weapon, armour — is scrap metal regardless of what it is made of. */
	private static boolean isGear(ItemStack stack) {
		return stack.isDamageableItem()
				|| stack.is(ItemTags.SWORDS) || stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.AXES)
				|| stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES)
				|| stack.is(ItemTags.HEAD_ARMOR) || stack.is(ItemTags.CHEST_ARMOR)
				|| stack.is(ItemTags.LEG_ARMOR) || stack.is(ItemTags.FOOT_ARMOR);
	}

	private static WasteFraction byTag(ItemStack stack) {
		if (stack.is(ModTags.Items.C_INGOTS)) {
			return WasteFraction.METAL;
		}
		if (stack.is(ItemTags.PLANKS) || stack.is(ItemTags.LOGS) || stack.is(ItemTags.WOOL)
				|| stack.is(ItemTags.WOOL_CARPETS) || stack.is(ItemTags.COALS)) {
			return WasteFraction.COMBUSTIBLE;
		}
		if (stack.is(ItemTags.STONE_BRICKS) || stack.is(ItemTags.DIRT) || stack.is(ItemTags.SAND)) {
			return WasteFraction.MINERAL;
		}
		return null;
	}

	/**
	 * The soils {@code #minecraft:dirt} does NOT cover.
	 *
	 * <p>In 26.2 that tag holds exactly three blocks — dirt, coarse dirt, rooted dirt — and grass, podzol,
	 * mycelium and mud are outside it (the same slimmed tag that once made wild kok-sagyz fail to
	 * generate). Without this list they fall through to the sound test, where grass block shares
	 * {@code SoundType.GRASS} with leaves and foliage and would be filed as BURNABLE: a player digging a
	 * hillside would be told his dirt is firewood, and the batch would grade as if he had shovelled in
	 * planks.
	 */
	private static boolean isSoil(net.minecraft.world.level.block.Block block) {
		return block == Blocks.GRASS_BLOCK || block == Blocks.PODZOL || block == Blocks.MYCELIUM
				|| block == Blocks.MUD || block == Blocks.DIRT_PATH || block == Blocks.FARMLAND
				|| block == Blocks.SOUL_SAND || block == Blocks.SOUL_SOIL || block == Blocks.CLAY;
	}

	/**
	 * Last structural guess: what the block sounds like when you break it. Crude on purpose — it only has
	 * to be better than "unknown", and it covers the long tail of modded blocks for free.
	 */
	private static WasteFraction bySound(BlockState state) {
		SoundType sound = state.getSoundType();
		if (sound == SoundType.WOOD || sound == SoundType.WOOL || sound == SoundType.BAMBOO
				|| sound == SoundType.GRASS || sound == SoundType.SCAFFOLDING) {
			return WasteFraction.COMBUSTIBLE;
		}
		if (sound == SoundType.METAL || sound == SoundType.COPPER || sound == SoundType.CHAIN
				|| sound == SoundType.ANVIL || sound == SoundType.LANTERN) {
			return WasteFraction.METAL;
		}
		if (sound == SoundType.STONE || sound == SoundType.DEEPSLATE || sound == SoundType.GRAVEL
				|| sound == SoundType.SAND || sound == SoundType.GLASS || sound == SoundType.TUFF
				|| sound == SoundType.CALCITE || sound == SoundType.BASALT || sound == SoundType.NETHERRACK) {
			return WasteFraction.MINERAL;
		}
		return null;
	}

	/** One item's contribution to a batch. */
	public record WasteProfile(int mass, WasteFraction fraction) {
	}
}

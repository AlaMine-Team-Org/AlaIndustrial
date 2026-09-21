package dev.alaindustrial.item.tool;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.BlockTransformer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Read-only view of vanilla's tilling rules, used by {@link ElectricHoeItem#wouldTill} to answer "would a
 * hoe do anything at all here?" <b>without</b> doing it (MOD-389).
 *
 * <h2>Why this class exists at all</h2>
 * A hoe's effect is not a method that can be called in "simulate" mode: {@code BlockTransformer} has one
 * entry point, {@code transformBlock}, and it writes. The applicability half of that method is, however,
 * reachable on its own — a transform applies when the clicked face is not in its {@code disallowedFaces}
 * and its {@code BlockStateProvider} offers a state for this position — and that is what is re-asked here.
 * Both halves read the world and write nothing.
 *
 * <h2>Why it is no longer a HoeItem subclass (26.3)</h2>
 * Until 26.3 the rules lived in {@code HoeItem.TILLABLES}, a {@code protected static} map that only a
 * subclass of {@code HoeItem} could read — which was the sole reason this class extended anything.
 * That map is gone. Tilling is now data-driven: a hoe carries a {@code minecraft:block_transformer}
 * component pointing at the {@code minecraft:hoe} entry of the {@code block_transformer} registry, whose
 * contents come from {@code data/minecraft/block_transformer/hoe.json}. Reading the registry keeps every
 * property the old map read had and gains one: a datapack that edits or extends the hoe's rules is
 * honoured, where the old map only saw what code put in it.
 *
 * <h2>Which transformer is asked</h2>
 * The one the stack in hand carries, not the one the registry keys under {@code minecraft:hoe}. Those
 * are the same object for our hoes — {@code ElectricHoeItem.electricHoeProperties} declares exactly that
 * key — but reading the stack is what makes the claim above ("the probe cannot answer differently from
 * the write") true by construction rather than by coincidence: {@code Item.useOn} reads the stack, so
 * anything that re-points this item's component (a modpack, a future tier) moves the probe with it. A
 * stack with no transformer answers "no", which is the same answer {@code Item.useOn} gives it.
 *
 * <h2>Loader scope</h2>
 * Components are the same object on both loaders — 26.3 removed the vanilla map NeoForge used to patch
 * out, and the {@code HOE_TILL} ability with it — so this answer is no longer the Fabric-only half of a
 * pair, and the NeoForge subclasses have nothing left to override.
 */
final class VanillaTillables {

	private VanillaTillables() {
	}

	/**
	 * {@code true} when a hoe's block transformer would convert the clicked block: some transform in
	 * {@code minecraft:hoe} accepts this face and offers a state for this position. Reads the world,
	 * writes nothing — the block swap, the loot, the sound and the item damage that {@code transformBlock}
	 * performs around this test are deliberately never reached.
	 *
	 * <p>A stack with no {@code minecraft:block_transformer} answers "no". A datapack that stripped the
	 * component has removed tilling, and the electric hoe should then pass the click on rather than charge
	 * for nothing.
	 */
	static boolean wouldTill(UseOnContext context) {
		Holder<BlockTransformer> hoe = context.getItemInHand().get(DataComponents.BLOCK_TRANSFORMER);
		if (hoe == null) {
			return false;
		}
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		Direction face = context.getClickedFace();
		for (BlockTransformer.BlockTransformData transform : hoe.value().transforms()) {
			// The face check is what used to be the DOWN-face rejection inside vanilla's onlyIfAirAbove
			// predicate: in 26.3 it is data (disallowed_faces) rather than code.
			if (transform.disallowedFaces().contains(face)) {
				continue;
			}
			// The same sampling call transformBlock makes, and with the same random source, so the probe
			// cannot answer differently from the write that follows it. The hoe's provider is rule-based
			// and draws nothing from it.
			if (transform.blockStateProvider().value()
					.getOptionalState(level, level.getRandom(), pos) != null) {
				return true;
			}
		}
		return false;
	}
}

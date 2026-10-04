package dev.alaindustrial.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.BlockTransformer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.BlockTransformers;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * The right-click block conversion of the mod's EU hoes and shovels — tilling and path-making — as a version
 * facade (MOD-704, ADR-036): every Minecraft line has a twin of this enum with the same constants and
 * signatures, only the bodies differ, so the four tools ({@code ElectricHoeItem}, {@code ElectricShovelItem}
 * and their diamond tips) are the same source on every line. A tool asks three things here and nothing else
 * about the mechanism: {@link #declare} it in its {@code Item.Properties}, {@link #apply} it from
 * {@code useOn}, and — the hoe, which charges for a till — {@link #wouldTill} before paying.
 *
 * <p>Our tools cannot extend the vanilla {@code HoeItem}/{@code ShovelItem}: their constructors apply
 * {@code Properties.hoe(...)}/{@code .shovel(...)}, which set {@code MAX_DAMAGE} and would give an EU tool a
 * durability bar. So the conversion is reached from outside, and how depends on the line.
 *
 * <h2>This twin: Minecraft 26.3 — a data component</h2>
 * 26.3 deleted {@code HoeItem} and {@code ShovelItem}. Tilling and path-making are now data: an item
 * carries a {@code minecraft:block_transformer} component pointing at an entry of the
 * {@code block_transformer} registry ({@code data/minecraft/block_transformer/hoe.json},
 * {@code shovel.json}), and {@code Item.useOn} is three lines that read it off the <i>stack in hand</i>
 * (verified by javap of the 26.3 {@code minecraft-merged.jar}, and of the NeoForge-patched one, which does
 * the same):
 *
 * <pre>{@code
 * Holder<BlockTransformer> t = context.getItemInHand().get(DataComponents.BLOCK_TRANSFORMER);
 * return t != null ? t.value().transformBlock(context) : InteractionResult.PASS;
 * }</pre>
 *
 * That is why the 26.2 way — calling {@code Items.DIAMOND_HOE.useOn(context)} — answers a silent
 * {@code PASS} here: the receiver no longer matters, our stack carried no component, so no till, no EU,
 * no message (the defect class MOD-378/379 shipped twice before, MOD-226). The fix is the vanilla
 * declaration: {@link #declare} adds the component exactly as {@code Properties.hoe(...)} does for a vanilla
 * hoe, and {@link #apply} is {@code Item.useOn}'s body. A datapack that edits {@code hoe.json} is honoured.
 *
 * <p>The component is <i>delayed</i> because {@code block_transformer} is a datapack registry: no
 * {@code Holder} for it exists while items are registered, and the initializer runs later, against the
 * loaded registries. It lands in the item's DEFAULT component map, so stacks saved by 26.2 worlds pick it
 * up on load without a datafixer — an {@code ItemStack} is always rebuilt as {@code item.components()}
 * plus the stack's own patch.
 *
 * <p>The transform cannot wear the tool out: for a non-stackable stack {@code transformBlock} ends in
 * {@code hurtAndBreak(item_damage_per_use, …)}, which returns immediately on {@code !isDamageableItem()} —
 * a no-op for a tool with no {@code MAX_DAMAGE}. It also bails out to {@code PASS} when the off-hand holds a
 * {@code minecraft:blocks_attacks} item and the player is not sneaking.
 *
 * <p>Campfire dousing — the shovel's other right-click — is not part of this transform on 26.3:
 * {@code CampfireBlock.useItemOn} decides it from the {@code #minecraft:douses_campfires} item tag before
 * the item's {@code useOn} is reached, and our shovels are in that tag.
 *
 * <p>Loader scope: components are the same object on both loaders — 26.3 removed the vanilla map NeoForge
 * used to patch out, and the {@code HOE_TILL}/{@code SHOVEL_FLATTEN} abilities with it — so this twin is
 * the answer on Fabric and on NeoForge alike.
 */
public enum RightClickTransform {

	/** Tilling: dirt, grass and dirt path → farmland, coarse dirt → dirt, rooted dirt → dirt + a hanging root. */
	HOE(BlockTransformers.HOE),

	/** Path-making: grass, dirt, podzol, mycelium, coarse and rooted dirt → dirt path. */
	SHOVEL(BlockTransformers.SHOVEL);

	private final ResourceKey<BlockTransformer> transformer;

	RightClickTransform(ResourceKey<BlockTransformer> transformer) {
		this.transformer = transformer;
	}

	/**
	 * Declares this conversion on an item's properties: the {@code minecraft:block_transformer} component
	 * pointing at this family's entry — the half of {@code Properties.hoe(...)}/{@code .shovel(...)} that
	 * carries the conversion. A tool that builds its own {@code Properties} (every diamond tip does) must
	 * declare it too, or it is the one tier whose right-click does nothing.
	 */
	public Item.Properties declare(Item.Properties properties) {
		return properties.delayedHolderComponent(DataComponents.BLOCK_TRANSFORMER, transformer);
	}

	/**
	 * Runs the conversion for a right-click with the tool: exactly {@code Item.useOn} on 26.3 — the
	 * transformer the stack in hand carries, or {@code PASS} for a stack that carries none.
	 */
	public InteractionResult apply(UseOnContext context) {
		Holder<BlockTransformer> carried = context.getItemInHand().get(DataComponents.BLOCK_TRANSFORMER);
		return carried != null ? carried.value().transformBlock(context) : InteractionResult.PASS;
	}

	/**
	 * Would a hoe convert the clicked block? Reads the world, writes nothing: asked by the electric hoe
	 * before its charge gate, so a flat hoe neither swallows an unrelated right-click nor reports an empty
	 * buffer to a player who was not tilling (MOD-389).
	 *
	 * <p>A hoe's effect cannot be called in a "simulate" mode — {@code BlockTransformer.transformBlock} has
	 * one entry point, and it writes — but its applicability half is reachable on its own: a transform
	 * applies when the clicked face is not in its {@code disallowedFaces} and its
	 * {@code BlockStateProvider} offers a state for this position. That is what is re-asked here, with the
	 * same sampling call and the same random source {@code transformBlock} uses, so the probe cannot answer
	 * differently from the write that follows it.
	 *
	 * <p>The transformer asked is the one the stack in hand carries, as {@code Item.useOn} does: anything
	 * that re-points the component (a modpack, a datapack) moves the probe with it, and a stack with no
	 * transformer answers "no" — the answer {@code Item.useOn} gives it too.
	 */
	public static boolean wouldTill(UseOnContext context) {
		Holder<BlockTransformer> carried = context.getItemInHand().get(DataComponents.BLOCK_TRANSFORMER);
		if (carried == null) {
			return false;
		}
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		Direction face = context.getClickedFace();
		for (BlockTransformer.BlockTransformData transform : carried.value().transforms()) {
			// The DOWN-face rejection inside vanilla's former onlyIfAirAbove predicate is data here
			// (disallowed_faces), not code.
			if (transform.disallowedFaces().contains(face)) {
				continue;
			}
			// The hoe's provider is rule-based and draws nothing from the random source.
			if (transform.blockStateProvider().value()
					.getOptionalState(level, level.getRandom(), pos) != null) {
				return true;
			}
		}
		return false;
	}
}

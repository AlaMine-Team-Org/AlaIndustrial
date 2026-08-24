package dev.alaindustrial.item.tool;

import com.mojang.datafixers.util.Pair;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Read-only view of vanilla's tilling table, used by {@link ElectricHoeItem#wouldTill} to answer "would a
 * hoe do anything at all here?" <b>without</b> doing it (MOD-389).
 *
 * <h2>Why this class exists at all</h2>
 * {@code HoeItem.TILLABLES} is {@code protected static} (verified with {@code javap -p} against
 * {@code minecraft-common.jar}, 26.2), so it is unreachable from {@code dev.alaindustrial.item.tool} —
 * except from a <i>subclass</i> of {@code HoeItem}, which is what this class is and is the only reason it
 * extends anything. It is never instantiated and never registered: the private constructor exists solely
 * because {@code HoeItem} has no no-arg constructor, and its arguments are never evaluated.
 *
 * <p>The alternative was to mirror the five vanilla entries here as a literal table. That was rejected: a
 * copy freezes the list at the version it was written against and, worse, silently drops the entries other
 * mods add — {@code TILLABLES} is a mutable {@code HashMap} precisely so they can. Reading the real map
 * also gets the real predicate ({@code onlyIfAirAbove}, which additionally rejects a click on the DOWN
 * face) instead of an approximation of it.
 *
 * <h2>Loader scope — this is the FABRIC answer</h2>
 * NeoForge patches {@code HoeItem.useOn} to ignore this map entirely and ask the block instead
 * ({@code IBlockExtension.getToolModifiedState}); it even deprecates the field with "patched out of
 * vanilla code". So on that loader this class would answer for a table nobody reads, and
 * {@code ElectricHoeItemNeoForge} overrides {@link ElectricHoeItem#wouldTill} with the loader's own
 * side-effect-free probe. Do not "simplify" by deleting that override.
 */
final class VanillaTillables extends HoeItem {

	/** Never called — see the class javadoc. {@code HoeItem} simply has no no-arg constructor. */
	private VanillaTillables() {
		super(ToolMaterial.DIAMOND, 0.0f, 0.0f, new Properties());
	}

	/**
	 * {@code true} when vanilla's {@code HoeItem.useOn} would convert the clicked block — the entry exists
	 * in {@code TILLABLES} <i>and</i> its predicate accepts this context. Reads the world, writes nothing:
	 * the {@code Consumer} half of the pair (the part that actually swaps the block and pops the hanging
	 * root) is deliberately never touched.
	 */
	// MOD-498 — HoeItem.TILLABLES is deprecated by NeoForge ("patched out of vanilla code"), not by
	// vanilla. Reading it is the whole point of this class: see the class javadoc, "Loader scope — this
	// is the FABRIC answer". On NeoForge the map is genuinely dead, and ElectricHoeItemNeoForge
	// overrides wouldTill with that loader's own probe instead of calling this.
	@SuppressWarnings("deprecation")
	static boolean wouldTill(UseOnContext context) {
		Pair<Predicate<UseOnContext>, Consumer<UseOnContext>> entry =
				TILLABLES.get(context.getLevel().getBlockState(context.getClickedPos()).getBlock());
		return entry != null && entry.getFirst().test(context);
	}
}

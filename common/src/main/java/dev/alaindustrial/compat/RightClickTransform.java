package dev.alaindustrial.compat;

import com.mojang.datafixers.util.Pair;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.context.UseOnContext;

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
 * <h2>This twin: Minecraft 26.2 — delegation to the vanilla diamond tools</h2>
 * The conversions are code in {@code HoeItem.useOn} and {@code ShovelItem.useOn}, and both were disassembled
 * before this was written (project rule 1). They are delegatable: they are {@code public}; their bytecode
 * contains no {@code aload_0} — they read nothing from {@code this}, only the {@link UseOnContext} and the
 * static {@code TILLABLES}/{@code FLATTENABLES} maps; and the only thing they do to the stack is
 * {@code getItemInHand().hurtAndBreak(...)}, which returns immediately on {@code !isDamageableItem()} — a
 * no-op for a tool with no {@code MAX_DAMAGE}. So {@link #apply} hands our context to
 * {@code Items.DIAMOND_HOE}/{@code Items.DIAMOND_SHOVEL}; the receiver has to be the vanilla item rather than
 * {@code super}, because {@code Item.useOn} (the tools' real superclass method) just returns {@code PASS}.
 * Delegation beats a copy of the tables: they are mutable maps other mods add to. The shovel's
 * {@code useOn} also douses a lit campfire, so that is part of {@link #SHOVEL} on this line. There is
 * nothing to declare: {@link #declare} returns the properties unchanged.
 *
 * <p>Loader scope: NeoForge patches {@code HoeItem.useOn}/{@code ShovelItem.useOn} to ask the block
 * ({@code getToolModifiedState}) instead of the maps, gated on the HELD item declaring the matching
 * {@code ItemAbility} — which is why the NeoForge registration of the four tools uses subclasses that
 * declare it ({@code Electric*ItemNeoForge}), and why the hoe's {@code wouldTill} is overridable there. On
 * Fabric this twin is the whole answer.
 */
public enum RightClickTransform {

	/** Tilling: dirt, grass and dirt path → farmland, coarse dirt → dirt, rooted dirt → dirt + a hanging root. */
	HOE,

	/** Path-making (grass, dirt, podzol, mycelium, coarse and rooted dirt → dirt path), and campfire dousing. */
	SHOVEL;

	/** Declares this conversion on an item's properties. On 26.2 the conversion is code, not a component. */
	public Item.Properties declare(Item.Properties properties) {
		return properties;
	}

	/**
	 * Runs the conversion for a right-click with the tool: the vanilla diamond tool's {@code useOn}. The item
	 * is read here, at click time, so this enum never initialises {@code Items} itself.
	 */
	public InteractionResult apply(UseOnContext context) {
		return (this == HOE ? Items.DIAMOND_HOE : Items.DIAMOND_SHOVEL).useOn(context);
	}

	/**
	 * Would a hoe convert the clicked block? Reads the world, writes nothing: asked by the electric hoe
	 * before its charge gate, so a flat hoe neither swallows an unrelated right-click nor reports an empty
	 * buffer to a player who was not tilling (MOD-389).
	 *
	 * <p>{@code true} when vanilla's {@code HoeItem.useOn} would convert the clicked block — the entry exists
	 * in {@code TILLABLES} <i>and</i> its predicate accepts this context (vanilla's {@code onlyIfAirAbove}
	 * also rejects a click on the DOWN face). The {@code Consumer} half of the pair (the part that swaps the
	 * block and pops the hanging root) is deliberately never touched. This is the Fabric answer; on NeoForge,
	 * which patches the map out of the flow, the hoe's subclass answers from {@code getToolModifiedState}.
	 */
	public static boolean wouldTill(UseOnContext context) {
		return Tillables.wouldTill(context);
	}

	/**
	 * Read-only view of {@code HoeItem.TILLABLES}, which is {@code protected static} (verified with
	 * {@code javap -p}, 26.2) and so reachable only from a subclass of {@code HoeItem} — the sole reason this
	 * class extends anything. It is never instantiated and never registered: the private constructor exists
	 * because {@code HoeItem} has no no-arg constructor, and its arguments are never evaluated.
	 */
	private static final class Tillables extends HoeItem {

		private Tillables() {
			super(ToolMaterial.DIAMOND, 0.0f, 0.0f, new Properties());
		}

		// MOD-498 — HoeItem.TILLABLES is deprecated by NeoForge ("patched out of vanilla code"), not by
		// vanilla. Reading it is the whole point here: it is the FABRIC answer, and on NeoForge the hoe's
		// subclass overrides wouldTill with that loader's own probe instead of calling this.
		@SuppressWarnings("deprecation")
		static boolean wouldTill(UseOnContext context) {
			Pair<Predicate<UseOnContext>, Consumer<UseOnContext>> entry =
					TILLABLES.get(context.getLevel().getBlockState(context.getClickedPos()).getBlock());
			return entry != null && entry.getFirst().test(context);
		}
	}
}

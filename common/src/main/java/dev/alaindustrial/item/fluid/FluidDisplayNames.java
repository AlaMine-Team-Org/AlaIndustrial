package dev.alaindustrial.item.fluid;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;

/**
 * The player-facing name of a fluid, wherever the mod has to print one: the GUI tank gauge's tooltip
 * ({@code FluidGauge}) and the Vacuum Capsule's item name ({@code CapsuleInteractions}). One method,
 * because the two used to carry the same three lines each and the second copy is how a fluid ends up
 * named correctly in one place and "Unknown fluid" in the other.
 *
 * <p>Three sources, in order:
 * <ol>
 *   <li><b>the placed block's name</b> — "Water", "Lava", "Oil". The right answer for any fluid that
 *       has terrain: it is the name the player already sees on the bucket and in the world;</li>
 *   <li><b>{@code fluid.<namespace>.<path>}</b> — for a fluid with NO block. That is not an exotic
 *       case invented for the future: steam (MOD-468) is exactly this, and before this key existed the
 *       reactor's own working fluid printed "Unknown fluid" in both places, because both read the name
 *       off a block that is air;</li>
 *   <li><b>"Unknown fluid"</b> — a foreign pipe-only fluid whose mod ships no such key. The original
 *       fallback, now reached only when there is genuinely nothing to print.</li>
 * </ol>
 *
 * <p>The middle step asks {@link Language} whether the key resolves rather than handing back
 * {@code Component.translatable(key)} unconditionally: an unknown key renders as the raw key text
 * ("fluid.somemod.plasma"), which is worse for the player than "Unknown fluid".
 *
 * <p><b>Known limit of that question.</b> {@code Language.getInstance()} is the CLIENT language
 * wherever a name is actually drawn, and there it knows every mod key. On a <i>Fabric dedicated
 * server</i> it is {@code Language.DEFAULT_INSTANCE}, which loads only vanilla's own
 * {@code /assets/minecraft/lang/en_us.json} (NeoForge injects mod lang files there, Fabric does
 * not) — so a name built server-side for a blockless fluid falls through to "Unknown fluid".
 * That is what the old code did for such a fluid in every case, so nothing regressed; it just
 * means the improvement lands on the client side, which is where item names and gauge tooltips
 * are drawn.
 */
public final class FluidDisplayNames {
	private FluidDisplayNames() {
	}

	/** Fallback label, shared with the filled capsule's item name. */
	private static final String UNKNOWN_KEY = "item.alaindustrial.filled_vacuum_capsule.fluid_unknown";

	public static Component of(Fluid fluid) {
		BlockState legacy = fluid.defaultFluidState().createLegacyBlock();
		if (!legacy.isAir()) {
			return legacy.getBlock().getName();
		}
		Identifier id = BuiltInRegistries.FLUID.getKey(fluid);
		if (id != null) {
			String key = Util.makeDescriptionId("fluid", id);
			if (Language.getInstance().has(key)) {
				return Component.translatable(key);
			}
		}
		return Component.translatable(UNKNOWN_KEY);
	}
}

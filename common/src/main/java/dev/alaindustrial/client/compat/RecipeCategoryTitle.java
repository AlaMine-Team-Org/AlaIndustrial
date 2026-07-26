package dev.alaindustrial.client.compat;

import dev.alaindustrial.block.entity.IncubatorMode;
import dev.alaindustrial.block.entity.SawmillMode;
import dev.alaindustrial.registry.ModRecipes;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Titles for the recipe-viewer categories, shared by the Fabric REI plugin and the NeoForge JEI one.
 *
 * <p>A machine that works several recipe families gets one category per family, and titling them all
 * with the block name alone tells the player nothing: the progress-arrow tooltip listed "Sawmill,
 * Sawmill, Sawmill, Sawmill" and "Incubator, Incubator, Incubator". Each family is therefore named
 * after the mode that works it — "Sawmill: Planks", "Incubator: Duplication" — built from the block's
 * own name plus the mode key the machine's own screen already uses, so all twenty locales are covered
 * without a single new lang string.
 *
 * <p><b>Why this lives in one place.</b> The sawmill and the incubator each grew their own copy of
 * this composition on separate branches, and the copies collided on merge — resolving that by keeping
 * either side would have silently un-named the other machine's tabs. One resolver consulted by both
 * loaders means a new mode-driven machine is registered here once instead of in four call sites that
 * can drift apart.
 */
public final class RecipeCategoryTitle {

	private RecipeCategoryTitle() {
	}

	/**
	 * The category title for one recipe family: "&lt;block&gt;: &lt;mode&gt;" when some machine mode
	 * works that family, otherwise the block's plain name (every single-family machine).
	 */
	public static Component of(ModRecipes.Kind kind, Component blockName) {
		String modeKey = modeKeyOf(kind);
		if (modeKey == null) {
			return blockName;
		}
		return Component.empty().append(blockName).append(": ").append(Component.translatable(modeKey));
	}

	/** The lang key of the mode that works this family, or {@code null} if no mode claims it. */
	@Nullable
	private static String modeKeyOf(ModRecipes.Kind kind) {
		SawmillMode sawing = SawmillMode.forKind(kind);
		if (sawing != null) {
			return sawing.translationKey();
		}
		IncubatorMode mutation = IncubatorMode.forKind(kind);
		if (mutation != null) {
			return mutation.translationKey();
		}
		return null;
	}
}

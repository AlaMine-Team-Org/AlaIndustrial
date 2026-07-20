package dev.alaindustrial.mixin.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read-only access to a container screen's {@code leftPos}/{@code topPos} (MOD-133). Both fields are
 * {@code protected} with no public getter in 26.2, but the inventory profile button must anchor to the
 * GUI's top-left corner (which shifts when the recipe book opens). No injected logic — the same
 * low-risk accessor pattern as {@link ItemTintSourcesAccessor}.
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Accessor("leftPos")
	int alaindustrial$getLeftPos();

	@Accessor("topPos")
	int alaindustrial$getTopPos();

	@Accessor("imageWidth")
	int alaindustrial$getImageWidth();
}

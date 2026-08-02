package dev.alaindustrial.mixin.client;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the vanilla late-bound item-model registry so the mod can add its own {@code type} to
 * {@code assets/.../items/*.json}. Same low-risk accessor pattern as {@link ItemTintSourcesAccessor},
 * and the same reason: {@code ItemModels.ID_MAPPER} is private, but it is a
 * {@link ExtraCodecs.LateBoundIdMapper} whose codec resolves on parse, so an entry added at client
 * init is live by the time the model definitions are read.
 */
@Mixin(ItemModels.class)
public interface ItemModelsAccessor {
	@Accessor("ID_MAPPER")
	static ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends ItemModel.Unbaked>>
			alaindustrial$idMapper() {
		throw new AssertionError();
	}
}

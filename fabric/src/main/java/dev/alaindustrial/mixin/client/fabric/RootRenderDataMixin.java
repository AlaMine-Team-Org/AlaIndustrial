package dev.alaindustrial.mixin.client.fabric;

import dev.alaindustrial.block.entity.KokSagyzRootBlockEntity;
import net.fabricmc.fabric.api.blockgetter.v2.RenderDataBlockEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(KokSagyzRootBlockEntity.class)
public abstract class RootRenderDataMixin implements RenderDataBlockEntity {
	@Override public Object getRenderData() {
		return ((KokSagyzRootBlockEntity) (Object) this).soil();
	}
}

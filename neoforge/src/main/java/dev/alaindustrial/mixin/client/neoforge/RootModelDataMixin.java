package dev.alaindustrial.mixin.client.neoforge;

import dev.alaindustrial.block.entity.KokSagyzRootBlockEntity;
import dev.alaindustrial.client.neoforge.NeoForgeRootSoilModels;
import net.neoforged.neoforge.common.extensions.IBlockEntityExtension;
import net.neoforged.neoforge.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KokSagyzRootBlockEntity.class)
public abstract class RootModelDataMixin implements IBlockEntityExtension {
	@Override public ModelData getModelData() {
		return ModelData.of(NeoForgeRootSoilModels.SOIL, ((KokSagyzRootBlockEntity) (Object) this).soil());
	}
	@Inject(method = {"setSoil", "loadAdditional"}, at = @At("RETURN"))
	private void refreshSoil(CallbackInfo ci) {
		KokSagyzRootBlockEntity root = (KokSagyzRootBlockEntity) (Object) this;
		if (root.getLevel() != null && root.getLevel().isClientSide()) {
			requestModelDataUpdate();
		}
	}
}

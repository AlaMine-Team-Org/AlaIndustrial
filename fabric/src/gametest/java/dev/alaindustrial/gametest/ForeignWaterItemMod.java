package dev.alaindustrial.gametest;

import dev.alaindustrial.core.fluid.FluidAmounts;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantItemStorage;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.material.Fluids;

/**
 * Stands in for another mod's water canister (MOD-912), so the cable wash is proven to pay by the amount
 * through {@code FluidStorage.ITEM} for a container the mod under test has never heard of.
 *
 * <p>Same reasoning as {@link ForeignEnergyItemMod}: this entrypoint belongs to the gametest mod, never
 * ships, and registers through the same lookup a real mod would use. The canister is
 * {@link CableWashSourceScenarios#FOREIGN_CANISTER}; it holds water only, up to a bucket, kept in
 * millibuckets in the vanilla {@code repair_cost} component — a component the mod does not read, like a real
 * foreign mod's — and gives any amount it holds. Its NeoForge twin is {@code ForeignWaterItemStandIn}.
 */
public class ForeignWaterItemMod implements ModInitializer {

	@Override
	public void onInitialize() {
		FluidStorage.ITEM.registerForItems((stack, context) -> new Canister(context),
				CableWashSourceScenarios.FOREIGN_CANISTER);
	}

	private static final class Canister extends SingleVariantItemStorage<FluidVariant> {
		Canister(ContainerItemContext context) {
			super(context);
		}

		@Override
		protected FluidVariant getBlankResource() {
			return FluidVariant.blank();
		}

		@Override
		protected FluidVariant getResource(ItemVariant item) {
			return mb(item) > 0 ? FluidVariant.of(Fluids.WATER) : FluidVariant.blank();
		}

		@Override
		protected long getAmount(ItemVariant item) {
			return FluidAmounts.toDroplets(mb(item));
		}

		@Override
		protected long getCapacity(FluidVariant variant) {
			return FluidAmounts.toDroplets(FluidAmounts.BUCKET);
		}

		@Override
		protected boolean canInsert(FluidVariant variant) {
			return false;
		}

		@Override
		protected ItemVariant getUpdatedVariant(ItemVariant current, FluidVariant variant, long droplets) {
			int mb = (int) (droplets / FluidAmounts.FABRIC_DROPLETS_PER_MB);
			return current.withComponents(DataComponentPatch.builder().set(DataComponents.REPAIR_COST, mb).build());
		}

		private static int mb(ItemVariant item) {
			Integer stored = item.getComponents().get(DataComponents.REPAIR_COST);
			return stored == null ? 0 : stored;
		}
	}
}

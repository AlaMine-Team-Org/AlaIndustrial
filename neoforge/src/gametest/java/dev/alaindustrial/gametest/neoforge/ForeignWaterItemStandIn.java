package dev.alaindustrial.gametest.neoforge;

import dev.alaindustrial.core.fluid.FluidAmounts;
import dev.alaindustrial.gametest.CableWashSourceScenarios;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.ItemAccessResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Stands in for another mod's water canister on the NeoForge lane (MOD-912) — the twin of the Fabric
 * {@code ForeignWaterItemMod}, wired at startup by {@link NeoForgeGameTestBootstrap} behind the same
 * production guard as {@link ForeignEnergyItemStandIn}. The canister is
 * {@link CableWashSourceScenarios#FOREIGN_CANISTER}; it holds water only, up to a bucket, in millibuckets in
 * the vanilla {@code repair_cost} component, and gives any amount it holds.
 */
public final class ForeignWaterItemStandIn {
	private ForeignWaterItemStandIn() {
	}

	/** Register the stand-in capability — dev/gametest environments only. */
	public static void register(RegisterCapabilitiesEvent event) {
		if (FMLEnvironment.isProduction()) {
			return;
		}
		event.registerItem(Capabilities.Fluid.ITEM, (stack, access) -> new Canister(access),
				CableWashSourceScenarios.FOREIGN_CANISTER);
	}

	private static final class Canister extends ItemAccessResourceHandler<FluidResource> {
		Canister(ItemAccess access) {
			super(access, 1);
		}

		private static int mb(ItemResource resource) {
			Integer stored = resource.getComponents().get(DataComponents.REPAIR_COST);
			return stored == null ? 0 : stored;
		}

		@Override
		protected FluidResource getResourceFrom(ItemResource resource, int index) {
			return mb(resource) > 0 ? FluidResource.of(Fluids.WATER) : FluidResource.EMPTY;
		}

		@Override
		protected int getAmountFrom(ItemResource resource, int index) {
			return mb(resource);
		}

		@Override
		protected ItemResource update(ItemResource resource, int index, FluidResource newResource, int newAmount) {
			return resource.with(DataComponents.REPAIR_COST, newAmount);
		}

		@Override
		protected int getCapacity(int index, FluidResource resource) {
			return (int) FluidAmounts.BUCKET;
		}

		@Override
		public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
			return 0;
		}
	}
}

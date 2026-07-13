package dev.alaindustrial.core.fabric;

import dev.alaindustrial.item.ItemFluid;
import dev.alaindustrial.registry.ModItems;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.base.FullItemFluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.base.BlankVariantView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.InsertionOnlyStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Fabric item-side fluid capability for the Vacuum Capsule (MOD-063) — the item counterpart of the
 * block-side {@link TankAsFluidStorage}. Lets other mods' fluid pipes/tanks fill or drain a capsule that
 * sits in an inventory or machine slot, exactly one bucket at a time (all-or-nothing, like the pump).
 *
 * <ul>
 * <li><b>Filled capsule</b> — the ready-made {@link FullItemFluidStorage}: extractable, gives up its one
 *     bucket and maps back to an empty capsule. The custom empty-mapping is used (not the component-copying
 *     default) so the drained capsule carries no {@code capsule_fluid} component — an emptied capsule and a
 *     crafted one are identical and stack.</li>
 * <li><b>Empty capsule</b> — a small insert-only storage: accepts one bucket of <em>any</em> fluid and
 *     exchanges to a filled capsule carrying that fluid (Fabric's {@code EmptyItemFluidStorage} is
 *     single-fluid, so it cannot be reused here).</li>
 * </ul>
 *
 * <p>Units are Fabric droplets ({@link FluidConstants#BUCKET} = 81000 = one bucket), the native unit of the
 * item capability — matching the block-side conversion in {@link TankAsFluidStorage}.
 */
public final class CapsuleItemFluidStorage {
	private CapsuleItemFluidStorage() {
	}

	/**
	 * Register both capsule fluid providers. Called once from the Fabric entrypoint at mod init. Uses
	 * {@code combinedItemApiProvider} (per-item combined event) rather than {@code ITEM.registerForItems}
	 * — the path Fabric documents for item fluid storages, so other mods can still layer providers onto
	 * the capsule. The current stack is read from the context's item variant.
	 */
	public static void register() {
		FluidStorage.combinedItemApiProvider(ModItems.VACUUM_CAPSULE)
				.register(EmptyCapsuleStorage::new);
		FluidStorage.combinedItemApiProvider(ModItems.FILLED_VACUUM_CAPSULE).register(context -> {
			Fluid fluid = ItemFluid.get(context.getItemVariant().toStack());
			if (fluid == Fluids.EMPTY) {
				return null;
			}
			return new FullItemFluidStorage(context, full -> ItemVariant.of(ModItems.VACUUM_CAPSULE),
					FluidVariant.of(fluid), FluidConstants.BUCKET);
		});
	}

	/** Insert-only storage on the empty capsule: one bucket of any fluid → a filled capsule of that fluid. */
	private static final class EmptyCapsuleStorage implements InsertionOnlyStorage<FluidVariant> {
		private final ContainerItemContext context;
		private final List<StorageView<FluidVariant>> blankView;

		private EmptyCapsuleStorage(ContainerItemContext context) {
			this.context = context;
			this.blankView = List.of(new BlankVariantView<>(FluidVariant.blank(), FluidConstants.BUCKET));
		}

		@Override
		public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
			StoragePreconditions.notBlankNotNegative(resource, maxAmount);
			if (!context.getItemVariant().isOf(ModItems.VACUUM_CAPSULE) || maxAmount < FluidConstants.BUCKET) {
				return 0;
			}
			ItemStack filled = new ItemStack(ModItems.FILLED_VACUUM_CAPSULE);
			ItemFluid.set(filled, resource.getFluid());
			if (context.exchange(ItemVariant.of(filled), 1, transaction) == 1) {
				return FluidConstants.BUCKET;
			}
			return 0;
		}

		@Override
		public Iterator<StorageView<FluidVariant>> iterator() {
			return blankView.iterator();
		}
	}
}

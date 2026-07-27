package dev.alaindustrial.item.fluid;

import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.world.item.DispensibleContainerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;

/**
 * Dispenser support for the oil bucket (MOD-238 audit fix).
 *
 * <p>The dispenser was asymmetric: it could FILL an oil bucket but not empty one. Emptying is
 * registered per item — {@code DispenseItemBehavior.bootStrap()} names every vanilla filled bucket
 * explicitly ({@code LAVA_BUCKET}, {@code WATER_BUCKET}, …) — while the empty-bucket behaviour is
 * generic ({@code blockState.getBlock() instanceof BucketPickup}) and therefore already scooped oil
 * up. This class is the missing half, re-implementing vanilla's anonymous {@code filledBucketBehavior}
 * (it is not reachable for reuse) for {@code alaindustrial:oil_bucket}.
 *
 * <p>Registered from BOTH loaders — Fabric during mod init, NeoForge inside
 * {@code FMLCommonSetupEvent#enqueueWork} because {@code DispenserBlock.registerBehavior} writes a
 * plain map and mod setup runs in parallel there.
 */
public class OilBucketDispenseBehavior extends DefaultDispenseItemBehavior {
	/** Vanilla's fallback: if the bucket cannot be emptied, spit the item out like any other. */
	private final DefaultDispenseItemBehavior fallback = new DefaultDispenseItemBehavior();

	/** Installs the behaviour for the mod's filled bucket. Idempotent — the registry is a map. */
	public static void register() {
		DispenserBlock.registerBehavior(ModContent.OIL_BUCKET.get(), new OilBucketDispenseBehavior());
	}

	@Override
	@SuppressWarnings("deprecation") // NeoForge deprecates this overload for an ItemStack-aware one
	public ItemStack execute(BlockSource source, ItemStack dispensed) {
		DispensibleContainerItem bucket = (DispensibleContainerItem) dispensed.getItem();
		BlockPos target = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
		Level level = source.level();
		if (bucket.emptyContents(null, level, target, null)) {
			bucket.checkExtraContent(null, level, dispensed, target);
			return this.consumeWithRemainder(source, dispensed, new ItemStack(Items.BUCKET));
		}
		return this.fallback.dispense(source, dispensed);
	}
}

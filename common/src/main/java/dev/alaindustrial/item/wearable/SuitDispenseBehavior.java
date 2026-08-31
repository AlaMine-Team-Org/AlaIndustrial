package dev.alaindustrial.item.wearable;

import dev.alaindustrial.core.radiation.RadiationMobs;
import dev.alaindustrial.registry.ModContent;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Dispenser support for the shielding suit (MOD-535).
 *
 * <p>Vanilla's {@code EquipmentDispenseItemBehavior} equips a villager just fine, but only if the
 * villager stands INSIDE the one block the dispenser faces — and a villager wanders. A player who
 * dispensed a suit at a villager that had stepped half a block aside got the pieces spat on the
 * floor, a bare (and doomed) villager, and later a zombie villager wearing the armor it had picked
 * up: exactly the report that looked like the shielding itself was broken.
 *
 * <p>This behavior widens the target to a 3×3×3 area centred on the facing block and accepts ONLY
 * the mobs the mechanic is FOR — villagers, wandering traders, cows (the same closed list
 * {@link RadiationMobs#isConvertible} keeps). With nobody suitable in range the piece is spat out
 * where the player can see it: a silent fallback onto whoever else happened to be standing at the
 * rig (the player, usually) is how the first live round produced four equip sounds, a bare
 * villager and a "shielding is broken" report. Everything else mirrors vanilla: the slot must be
 * one {@code canEquipWithDispenser} accepts, the piece lands via {@code setItemSlot}, a {@code Mob}
 * gets {@code setGuaranteedDrop} + {@code setPersistenceRequired}. The leather equip sound plays on
 * success — now meaning exactly one thing: a convertible mob got the piece.
 *
 * <p>Registered from BOTH loaders — Fabric during mod init, NeoForge inside
 * {@code FMLCommonSetupEvent#enqueueWork} because {@code DispenserBlock.registerBehavior} writes a
 * plain map and mod setup runs in parallel there (same arrangement as
 * {@link dev.alaindustrial.item.fluid.OilBucketDispenseBehavior}).
 */
public class SuitDispenseBehavior extends DefaultDispenseItemBehavior {

	/** Installs the behavior for all four suit pieces. Idempotent — the registry is a map. */
	public static void register() {
		SuitDispenseBehavior behavior = new SuitDispenseBehavior();
		DispenserBlock.registerBehavior(ModContent.SHIELDING_HELMET.get(), behavior);
		DispenserBlock.registerBehavior(ModContent.SHIELDING_CHESTPLATE.get(), behavior);
		DispenserBlock.registerBehavior(ModContent.SHIELDING_LEGGINGS.get(), behavior);
		DispenserBlock.registerBehavior(ModContent.SHIELDING_BOOTS.get(), behavior);
	}

	@Override
	@SuppressWarnings("deprecation") // NeoForge deprecates this overload for an ItemStack-aware one
	public ItemStack execute(BlockSource source, ItemStack dispensed) {
		LivingEntity wearer = findWearer(source, dispensed);
		if (wearer == null) {
			return super.execute(source, dispensed);
		}
		ServerLevel level = source.level();
		EquipmentSlot slot = wearer.getEquipmentSlotForItem(dispensed);
		wearer.setItemSlot(slot, dispensed.split(1));
		if (wearer instanceof Mob mob) {
			mob.setGuaranteedDrop(slot);
			mob.setPersistenceRequired();
		}
		Equippable equippable = dispensed.get(DataComponents.EQUIPPABLE);
		if (equippable != null) {
			level.playSound(null, wearer.getX(), wearer.getY(), wearer.getZ(),
					equippable.equipSound().value(), SoundSource.NEUTRAL, 1.0f, 1.0f);
		}
		return dispensed;
	}

	/**
	 * Who this dispense lands on, if anybody: a CONVERTIBLE mob only — villager, trader, cow — from
	 * the widened area, closest to the nozzle first. Anything else is a miss: the piece is spat out
	 * where the player can see it, because the first live round taught us what silent fallbacks cost
	 * (vanilla equips only the single facing block; an earlier draft of this behavior accepted any
	 * living entity and quietly dressed the PLAYER standing at the rig while the villager had
	 * wandered off — four equip sounds, a bare villager, and a bug report about broken shielding).
	 */
	private static LivingEntity findWearer(BlockSource source, ItemStack dispensed) {
		ServerLevel level = source.level();
		BlockPos facing = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
		Vec3 centre = Vec3.atCenterOf(facing);
		Vec3 nozzle = Vec3.atCenterOf(source.pos());
		return level.getEntitiesOfClass(Mob.class,
						AABB.ofSize(centre, 3.0, 3.0, 3.0), RadiationMobs::isConvertible).stream()
				.filter(mob -> mob.canEquipWithDispenser(dispensed))
				.min(Comparator.comparingInt(SuitDispenseBehavior::preference)
						.thenComparing(entity -> entity.distanceToSqr(nozzle)))
				.orElse(null);
	}

	/** Villagers and traders are what the feature is about; cows are the livestock half of it. */
	private static int preference(LivingEntity entity) {
		return entity instanceof AbstractVillager ? 0 : 1;
	}
}

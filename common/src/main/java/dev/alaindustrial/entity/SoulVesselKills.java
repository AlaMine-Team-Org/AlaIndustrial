package dev.alaindustrial.entity;

import dev.alaindustrial.item.misc.SoulVesselItem;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Soul Vessel kill accounting (MOD-278): the one branch both loaders' death hooks call when any
 * living entity dies (Fabric {@code ServerLivingEntityEvents.AFTER_DEATH}, NeoForge
 * {@code LivingDeathEvent} — the same two listeners that already cancel teleport warmups).
 *
 * <p><b>Strictly personal kills.</b> The check is {@code source.getEntity() instanceof
 * ServerPlayer} — the damage source's owner at the moment of death — and deliberately NOT
 * {@link LivingEntity#getKillCredit()}: kill credit attributes a tamed wolf's kill to its owner
 * (via {@code resolvePlayerResponsibleForDamage}) and remembers the player for 100 ticks after
 * their last hit, both of which are "assisted" kills the vessel must not count. An arrow still
 * counts — for a projectile {@code getEntity()} is the shooter.
 *
 * <p>With several vessels in the inventory, the first non-full one in slot order fills up —
 * hotbar first, matching how players expect "the top vessel" to charge.
 */
public final class SoulVesselKills {
	private SoulVesselKills() {
	}

	/**
	 * Death-hook branch: if {@code died} is an actively hostile mob and the killing blow came from a
	 * player personally, put one soul into the killer's first non-full vessel. Returns whether a
	 * soul was banked (for the gametests).
	 */
	public static boolean onDeath(LivingEntity died, DamageSource source) {
		if (!(died.level() instanceof ServerLevel serverLevel)) {
			return false;
		}
		if (!HostileThreat.isCountableKill(died, serverLevel)) {
			return false;
		}
		if (!(source.getEntity() instanceof ServerPlayer killer)) {
			return false;
		}
		ItemStack vessel = firstVesselWithRoom(killer);
		if (vessel.isEmpty()) {
			return false;
		}
		SoulVesselItem.setKills(vessel, SoulVesselItem.kills(vessel) + 1);
		return true;
	}

	/** The first Soul Vessel in slot order that still has room, or {@code ItemStack.EMPTY}. */
	private static ItemStack firstVesselWithRoom(ServerPlayer player) {
		int size = player.getInventory().getContainerSize();
		for (int slot = 0; slot < size; slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(ModContent.SOUL_VESSEL.get()) && SoulVesselItem.hasRoom(stack)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}
}

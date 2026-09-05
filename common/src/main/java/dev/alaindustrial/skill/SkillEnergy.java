package dev.alaindustrial.skill;

import dev.alaindustrial.Config;
import dev.alaindustrial.item.energy.BatteryItem;
import dev.alaindustrial.item.energy.PouchItem;
import dev.alaindustrial.item.wearable.EnergyPackItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * What the Energy branch actually does (MOD-483) — the arithmetic of its seven nodes, in one place.
 *
 * <p>Each method is called from the single point in the mod that owns the number it changes, named in
 * {@link SkillEffects}. Keeping them together rather than scattering literals through
 * {@code ItemEnergy}, the pack and the armour means the branch can be re-balanced by reading one file,
 * and that no call site invents its own rounding.
 *
 * <p><b>Server-side only in practice.</b> {@link SkillStore} is bound on the server, so on a client
 * these all fall back to the unmodified value — which is correct: EU is debited server-side, and a
 * client that guessed a discount would only disagree with the server about a number it does not own.
 */
public final class SkillEnergy {

	// Every number this class applies is a Config knob — see the MOD-483 block in Config.

	private SkillEnergy() {
	}

	/**
	 * Whether this stack merely CARRIES charge for other items rather than doing work with it.
	 *
	 * <p>The difference is not cosmetic, it is the line between a discount and a dupe. A pack handing
	 * out EU debits itself through the same {@code spend} a drill uses, so a discount there would let it
	 * deliver a hundred and pay ninety — ten EU per transfer conjured out of nothing, several times a
	 * second. A refund into the pack for the pack's own payout does the same thing again. Carriers are
	 * therefore charged the full price and earn no refund; only work is discounted.
	 */
	private static boolean carriesForOthers(ItemStack stack) {
		Item item = stack.getItem();
		return item instanceof EnergyPackItem || item instanceof PouchItem || item instanceof BatteryItem;
	}

	/** The build carried by this entity, or empty for anything that is not a server player. */
	public static SkillBuild of(@Nullable Entity entity) {
		return entity instanceof ServerPlayer player ? SkillStore.build(player) : SkillBuild.EMPTY;
	}

	private static boolean has(@Nullable Entity owner, SkillSlot slot) {
		return of(owner).has(SkillBranch.ENERGY, slot);
	}

	/**
	 * Frugal Stroke — the price of one powered action, after the owner's skills.
	 *
	 * <p>Rounded <b>up</b> on purpose. A cheap action is where a percentage does the most damage: the
	 * magnet costs 2 EU per item, and rounding down would hand it a 50 % discount where the drill gets
	 * its intended 10 %. Rounding up also leaves the armour's 1 EU/s upkeep untouched, which is right —
	 * that number has a node of its own.
	 */
	public static long toolCost(ItemStack stack, long eu, @Nullable Entity owner) {
		if (eu <= 0 || carriesForOthers(stack) || !has(owner, SkillSlot.IN)) {
			return eu;
		}
		return Math.max(1L, Math.ceilDiv(eu * Config.skillFrugalStrokePercent, 100L));
	}

	/**
	 * Frugal Armour — whether this second of powered-armour upkeep is free.
	 *
	 * <p>Counted in seconds rather than taken as a percentage because the upkeep is <b>one</b> EU: a
	 * percentage of 1 rounds to either nothing or everything, so "every third second is free" is the
	 * only honest way to express a third off.
	 */
	public static boolean armourUpkeepFree(@Nullable Entity wearer, long gameTime) {
		if (!has(wearer, SkillSlot.B1)) {
			return false;
		}
		return (gameTime / 20L) % Math.max(1, Config.skillFrugalArmourEverySeconds) == 0L;
	}

	/**
	 * Quiet Stance — powered armour spends no upkeep while its wearer stands still.
	 *
	 * <p>Measured on horizontal movement only: a player standing on a lift or falling is not walking,
	 * and charging them for gravity would read as a bug.
	 */
	public static boolean armourUpkeepIdle(@Nullable Entity wearer) {
		if (!has(wearer, SkillSlot.B2) || wearer == null) {
			return false;
		}
		return wearer.getDeltaMovement().horizontalDistanceSqr() < 1.0E-6;
	}

	/** Quick Docking — how much EU a powered item may accept per tick, after skills. */
	public static long inputRate(long rate, @Nullable Entity owner) {
		if (rate <= 0 || !has(owner, SkillSlot.MID)) {
			return rate;
		}
		return Math.round(rate * Config.skillQuickDockFactor);
	}

	/**
	 * Same ceiling, asked by a BLOCK rather than by the player themselves.
	 *
	 * <p>The Battery Box and the CESU charge from their own slot and never touch
	 * {@code PlayerEuDistributor}, so without this overload Quick Docking would have been silently
	 * missing from exactly the place its description names — a charger the player walks up to.
	 */
	public static long inputRate(long rate, @Nullable Level level, @Nullable UUID owner) {
		if (rate <= 0 || !OwnerPresence.skillsOf(level, owner).has(SkillBranch.ENERGY, SkillSlot.MID)) {
			return rate;
		}
		return Math.round(rate * Config.skillQuickDockFactor);
	}

	/** Recuperator — EU returned to the worn pack out of a spend of {@code eu}. Zero without the node. */
	public static long recuperated(ItemStack stack, long eu, @Nullable Entity owner) {
		if (eu <= 0 || carriesForOthers(stack) || !has(owner, SkillSlot.A2)) {
			return 0L;
		}
		return eu * Config.skillRecuperatorPercent / 100L;
	}

	/** Shared Bus — whether the worn pack also tops up equipped gear. */
	public static boolean packFeedsEquipped(@Nullable Entity owner) {
		return has(owner, SkillSlot.A1);
	}

	/** Field Circuit — whether a pack works from any inventory slot, not only worn. */
	public static boolean packWorksFromBag(@Nullable Entity owner) {
		return has(owner, SkillSlot.CAP);
	}
}

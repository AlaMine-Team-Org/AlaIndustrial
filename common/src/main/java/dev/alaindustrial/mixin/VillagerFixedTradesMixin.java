package dev.alaindustrial.mixin;

import dev.alaindustrial.registry.ModProfessions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MOD-772: gives every Industrialist the fixed trades of the level being filled, on top of the random ones.
 *
 * <p>A profession has ONE trade set per level, and {@code addOffersFromTradeSet} deals {@code amount} of its
 * trades at random — there is no way in data to make one trade appear for every villager without making the
 * whole level deterministic. So right after vanilla has dealt the level's own set, this adds the level's
 * {@code trade_set/industrialist/level_N_fixed} through the SAME vanilla method, which builds the loot
 * context the vanilla way; the set names no {@code random_sequence}, so that context draws from the level's
 * random. The set's {@code amount} covers its whole pool, so all of it is added. A level without such a file
 * adds nothing ({@link ModProfessions#fixedTradesFor}).
 *
 * <p>{@code updateTrades} runs once per level — when the offers are first created
 * ({@code AbstractVillager.getOffers}) and on each level-up ({@code Villager.increaseMerchantCareer}); the
 * offers are then saved with the villager, so a reload does not run it again and nothing is duplicated.
 * The injection point is the vanilla call itself (shift AFTER), present with the same descriptor on both
 * lines. What follows it differs: on 26.3 {@code updateTrades} then re-prices for the current trading player
 * ({@code updateSpecialPrices}), on 26.2 it does not — on both lines the discounts are applied anyway when a
 * player opens the trade screen.
 *
 * <p>Lives in the optional mixin config: if another mod rewrites {@code updateTrades}, the energy order is
 * lost instead of the game failing to load; the {@code industrialist_energy_order_*} gametests catch a
 * missed injection.
 */
@Mixin(Villager.class)
public abstract class VillagerFixedTradesMixin extends AbstractVillager {

	private VillagerFixedTradesMixin(EntityType<? extends AbstractVillager> type, Level level) {
		super(type, level);
	}

	@Inject(method = "updateTrades", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/npc/villager/Villager;addOffersFromTradeSet("
					+ "Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/trading/MerchantOffers;"
					+ "Lnet/minecraft/resources/ResourceKey;)V",
			shift = At.Shift.AFTER))
	private void alaindustrial$addFixedTrades(ServerLevel level, CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		ModProfessions.fixedTradesFor(self.getVillagerData(), level.registryAccess())
				.ifPresent(fixed -> addOffersFromTradeSet(level, getOffers(), fixed));
	}
}

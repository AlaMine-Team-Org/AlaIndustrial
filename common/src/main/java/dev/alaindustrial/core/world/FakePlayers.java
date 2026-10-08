package dev.alaindustrial.core.world;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * A loader's fake player, standing in for a block that acts on the world on its owner's behalf (MOD-787).
 *
 * <p>The Block Breaker breaks blocks through the same {@code ServerPlayerGameMode.destroyBlock} path a
 * player does, so both loaders fire their block-break event there and a land-claim mod can refuse a
 * break in somebody else's claim. That needs a {@link ServerPlayer} carrying the machine owner's
 * profile, and each loader keeps its own: Fabric API's {@code FakePlayer}, NeoForge's
 * {@code FakePlayerFactory}. Same seam shape as the energy and item lookups: common code asks
 * {@link #get()}, each loader's entry point installs its implementation.
 */
public interface FakePlayers {

	/** The loader's fake player for {@code profile} in {@code level}; cached by the loader, not by us. */
	ServerPlayer get(ServerLevel level, GameProfile profile);

	FakePlayers[] INSTANCE = new FakePlayers[1];

	static void install(FakePlayers impl) {
		INSTANCE[0] = impl;
	}

	/** The installed seam; throws before a loader installed one, rather than break blocks as nobody. */
	static FakePlayers get() {
		FakePlayers impl = INSTANCE[0];
		if (impl == null) {
			throw new IllegalStateException("FakePlayers seam not installed by the loader");
		}
		return impl;
	}
}

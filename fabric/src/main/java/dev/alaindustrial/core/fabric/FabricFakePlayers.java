package dev.alaindustrial.core.fabric;

import com.mojang.authlib.GameProfile;
import dev.alaindustrial.core.world.FakePlayers;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Fabric API's fake player behind {@link FakePlayers} (MOD-787). */
public final class FabricFakePlayers implements FakePlayers {

	@Override
	public ServerPlayer get(ServerLevel level, GameProfile profile) {
		return FakePlayer.get(level, profile);
	}
}

package dev.alaindustrial.core.neoforge;

import com.mojang.authlib.GameProfile;
import dev.alaindustrial.core.world.FakePlayers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** NeoForge's fake player behind {@link FakePlayers} (MOD-787). */
public final class NeoForgeFakePlayers implements FakePlayers {

	@Override
	public ServerPlayer get(ServerLevel level, GameProfile profile) {
		return FakePlayerFactory.get(level, profile);
	}
}

package dev.alaindustrial.item.misc;

import dev.alaindustrial.client.guide.GuideBookClientAccess;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * The Guide Book (MOD-067): a read-only tutorial item. Right-click opens a full-screen, client-only
 * {@code GuideBookScreen} (no container, no {@code MenuType}, no networking) — its content is baked
 * per-locale JSON under {@code assets/alaindustrial/guide_book/}, generated from the OKF specs.
 *
 * <p><b>26.2 API (verified against sources, MOD-067 audit):</b> {@code use} returns
 * {@link InteractionResult} (not the removed {@code InteractionResultHolder}); the screen is opened
 * client-side only, guarded by {@link Level#isClientSide()}, so the server never classloads
 * {@code Minecraft}. Client on {@code SUCCESS} (arm swing), server on {@code CONSUME} (no-op).
 */
public class GuideBookItem extends Item {
	public GuideBookItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) {
			// Client-only screen opening: the Minecraft/GuideBookScreen references live in a class
			// under client.guide, reached only inside this branch, so the dedicated server / gametest
			// never loads client classes (guarded by ArchitectureRules.clientTypesStayInsideClientPackages).
			GuideBookClientAccess.open();
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.CONSUME;
	}
}

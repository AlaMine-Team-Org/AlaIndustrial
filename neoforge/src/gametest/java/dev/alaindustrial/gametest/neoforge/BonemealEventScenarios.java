package dev.alaindustrial.gametest.neoforge;

import dev.alaindustrial.gametest.AlaGameTestHelper;
import dev.alaindustrial.registry.ModContent;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.BonemealEvent;

/**
 * Reinforced bone meal against NeoForge's {@link BonemealEvent} (MOD-906), the path only this loader has: another
 * mod cancelling the event, with or without "success". Either way the meal is not spent by us — on "success" the
 * other mod owns the stack, exactly as NeoForge's own bone meal leaves it to that mod; on a plain cancel the use is
 * refused.
 *
 * <p>The event bus cannot drop a lambda listener, so one listener is installed for the whole run and acts only on
 * the cells a scenario arms; every other test's bone meal goes through untouched.
 */
public final class BonemealEventScenarios {

	private static final Map<BlockPos, Boolean> ARMED = new ConcurrentHashMap<>();
	private static final AtomicBoolean LISTENING = new AtomicBoolean();
	private static final int STACK = 16;

	private BonemealEventScenarios() {
	}

	/**
	 * A mod cancels the event and reports success: the click is a success, the wheat is the mod's business (it
	 * stays at age 0 here), and our meal is not spent — no second charge on top of the mod's own.
	 *
	 * @implements MOD-906 — a cancelled-with-success event does not spend the meal twice.
	 */
	public static void handledByAnotherModSpendsNothing(GameTestHelper helper) {
		run(helper, true);
	}

	/**
	 * A mod cancels the event without success (a claim refusing the use): nothing grows, nothing is spent.
	 *
	 * @implements MOD-906 — a cancelled event refuses the meal.
	 */
	public static void deniedByAnotherModSpendsNothing(GameTestHelper helper) {
		run(helper, false);
	}

	private static void run(GameTestHelper helper, boolean successful) {
		listen();
		BlockPos rel = new BlockPos(2, 2, 2);
		helper.setBlock(rel.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7));
		helper.setBlock(rel, Blocks.WHEAT);
		BlockPos abs = helper.absolutePos(rel);
		BlockState before = helper.getBlockState(rel);
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.REINFORCED_BONE_MEAL.get(), STACK));
		ARMED.put(abs.immutable(), successful);
		try {
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
			player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
					hit);
		} finally {
			ARMED.remove(abs);
		}
		if (helper.getBlockState(rel) != before) {
			helper.fail("the meal grew the wheat although another mod cancelled the event: "
					+ helper.getBlockState(rel));
			return;
		}
		int count = player.getMainHandItem().getCount();
		if (count != STACK) {
			helper.fail("the stack holds " + count + ", expected " + STACK + " (cancelled, successful=" + successful
					+ ": our meal must not be spent)");
			return;
		}
		helper.succeed();
	}

	private static void listen() {
		if (LISTENING.compareAndSet(false, true)) {
			NeoForge.EVENT_BUS.addListener(BonemealEvent.class, event -> {
				Boolean successful = ARMED.get(event.getPos());
				if (successful != null) {
					event.setCanceled(true);
					event.setSuccessful(successful);
				}
			});
		}
	}
}

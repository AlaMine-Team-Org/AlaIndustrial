package dev.alaindustrial.client.render;

import dev.alaindustrial.block.KokSagyzRoots;
import dev.alaindustrial.block.entity.KokSagyzRootBlockEntity;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Bounded, personal inspection. All world access happens during frame extraction. */
public final class RootInspection {
	private static final int RADIUS = 4;
	private static final int MAX_PLANTS = 8;
	private static ClientLevel seenLevel;
	private static long lastScan = Long.MIN_VALUE;
	private static List<BlockPos> candidates = List.of();
	private static float opacity;
	private static Component hint;
	private static boolean wasActive;
	private RootInspection() { }

	public record Plant(BlockPos flower, KokSagyzRoots.Column column, BlockState upperSoil, BlockState lowerSoil, int upperTint, int lowerTint) { }
	public record Frame(List<Plant> plants, float opacity) {
		public static final Frame EMPTY = new Frame(List.of(), 0);
	}

	public static Frame extract(DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != seenLevel || mc.level == null || mc.player == null || mc.gui.screen() != null || mc.isPaused()) {
			seenLevel = mc.level;
			candidates = List.of();
			lastScan = Long.MIN_VALUE;
			opacity = 0;
			hint = null;
			wasActive = false;
			return Frame.EMPTY;
		}
		BlockPos focused = focusedPlant(mc);
		boolean active = mc.options.keyShift.isDown();
		float step = Math.min(1, delta.getRealtimeDeltaTicks() / 4.0f);
		opacity = active ? Math.min(1, opacity + step) : Math.max(0, opacity - step);
		hint = focused == null ? null : active ? status(KokSagyzRoots.inspect(mc.level, focused))
				: Component.translatable("hud.alaindustrial.root_inspect.hint", mc.options.keyShift.getTranslatedKeyMessage());
		long tick = mc.level.getGameTime();
		if (active && (!wasActive || tick - lastScan >= 5 || lastScan == Long.MIN_VALUE)) {
			lastScan = tick;
			List<BlockPos> found = new ArrayList<>();
			BlockPos centre = mc.player.blockPosition();
			for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-RADIUS, -3, -RADIUS), centre.offset(RADIUS, 3, RADIUS))) {
				if (mc.level.isLoaded(pos) && mc.level.getBlockState(pos).is(ModContent.KOK_SAGYZ.get()) && visible(mc, pos)) {
					found.add(pos.immutable());
				}
			}
			found.sort(Comparator.comparingDouble(pos -> pos.distToCenterSqr(mc.player.position())));
			candidates = List.copyOf(found.subList(0, Math.min(MAX_PLANTS, found.size())));
		}
		wasActive = active;
		if (opacity <= 0) {
			candidates = List.of();
			return Frame.EMPTY;
		}
		List<BlockPos> selected = new ArrayList<>(candidates);
		if (focused != null) {
			selected.remove(focused);
			selected.addFirst(focused);
		}
		List<Plant> plants = new ArrayList<>();
		for (BlockPos pos : selected) {
			if (plants.size() >= MAX_PLANTS) break;
			if (!mc.level.isLoaded(pos.below(2)) || !visible(mc, pos)) continue;
			KokSagyzRoots.Column column = KokSagyzRoots.inspect(mc.level, pos);
			if (column.depth() > 0) plants.add(new Plant(pos, column,
					KokSagyzRootBlockEntity.soilAt(mc.level, pos.below()),
					KokSagyzRootBlockEntity.soilAt(mc.level, pos.below(2)), tint(mc, pos.below()), tint(mc, pos.below(2))));
		}
		return new Frame(List.copyOf(plants), opacity);
	}

	private static int tint(Minecraft mc, BlockPos pos) {
		BlockState soil = KokSagyzRootBlockEntity.soilAt(mc.level, pos);
		var source = mc.getBlockColors().getTintSource(soil, 0);
		return source == null ? -1 : source.colorInWorld(soil, mc.level, pos);
	}

	private static boolean visible(Minecraft mc, BlockPos pos) {
		Vec3 eye = mc.player.getEyePosition();
		Vec3 target = Vec3.atCenterOf(pos);
		if (mc.player.position().distanceToSqr(target) > 25 || target.subtract(eye).dot(mc.player.getViewVector(1)) < 0) return false;
		BlockHitResult hit = mc.level.clip(new ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
		return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
	}

	private static BlockPos focusedPlant(Minecraft mc) {
		if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return null;
		BlockPos pos = hit.getBlockPos();
		BlockState state = mc.level.getBlockState(pos);
		if (state.is(ModContent.KOK_SAGYZ.get())) return pos;
		if (state.is(ModContent.KOK_SAGYZ_ROOT.get())) {
			return mc.level.getBlockState(pos.above()).is(ModContent.KOK_SAGYZ_ROOT.get()) ? pos.above(2) : pos.above();
		}
		return mc.level.getBlockState(pos.above()).is(ModContent.KOK_SAGYZ.get()) ? pos.above() : null;
	}

	private static Component status(KokSagyzRoots.Column column) {
		String state = column.depth() == 0 ? "empty" : column.harvestable() ? "ready" : column.growing() ? "growing" : "stopped";
		return Component.translatable("hud.alaindustrial.root_inspect." + state, column.depth());
	}

	/** Half the vanilla hotbar's width — the strip the hint must not sit on top of. */
	private static final int HOTBAR_HALF = 91;
	/** The off-hand slot, which vanilla draws on the side OPPOSITE the main hand. */
	private static final int OFFHAND_SLOT = 29;

	/**
	 * The hint sits at the bottom right, alongside the hotbar, rather than under the crosshair: it
	 * is an aside, and in the middle of the screen it covered exactly what the player was leaning in
	 * to look at. It clears the off-hand slot when the player is left-handed (vanilla then draws
	 * that slot on the right) and slides back inside the screen rather than running off it.
	 */
	public static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (hint == null || mc.gui.screen() != null || mc.gui.hud.isHidden()) {
			return;
		}
		int width = mc.font.width(hint);
		int left = graphics.guiWidth() / 2 + HOTBAR_HALF + 6;
		if (mc.options.mainHand().get() == HumanoidArm.LEFT) {
			left += OFFHAND_SLOT;
		}
		int y = graphics.guiHeight() - 16;
		if (left + width > graphics.guiWidth() - 4) {
			// A large GUI scale leaves no room beside the hotbar: sit above it rather than on it.
			left = Math.max(2, graphics.guiWidth() - width - 4);
			y -= 24;
		}
		graphics.fill(left - 4, y - 3, left + width + 4, y + 12, 0x99000000);
		graphics.text(mc.font, hint, left, y, 0xFFF0E7CC);
	}
}

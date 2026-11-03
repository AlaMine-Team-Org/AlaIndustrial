package dev.alaindustrial.client.tablet;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.alaindustrial.compat.client.Poses;
import dev.alaindustrial.item.misc.ReactorTabletItem;
import java.util.function.Consumer;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The first-person pose of the reactor tablet while it is being lifted, held open or lowered (MOD-946): both hands
 * bring it up from below the view and hold it upright in front of the face.
 *
 * <p>The arms are vanilla's two-handed map arms ({@code renderMapHand}); the layout is the two-handed map's, with its
 * pitch tilt and its lowering both driven by one value, {@code 1 − raise}: lowered, the tablet lies flat below the
 * view; raised, it stands upright at {@link ReactorTabletLayout#RAISED_DISTANCE}, where the unfolding screen
 * starts. The hand mixin of each line only supplies the two drawing calls, so the pose itself is the same source on
 * every line.
 */
public final class ReactorTabletHands {

	private ReactorTabletHands() {
	}

	/** How far the client's tablet is lifted right now: 0 when no animation is playing. */
	public static float raise() {
		return ReactorTabletTimeline.CLIENT.raise(Util.getMillis());
	}

	/**
	 * Which hand the animation holds the tablet in — the main hand first, as {@link ReactorTabletItem#heldTablet}
	 * picks it — or null when neither hand holds one.
	 */
	public static @Nullable InteractionHand tabletHand(ItemStack mainHand, ItemStack offHand) {
		if (mainHand.getItem() instanceof ReactorTabletItem) {
			return InteractionHand.MAIN_HAND;
		}
		return offHand.getItem() instanceof ReactorTabletItem ? InteractionHand.OFF_HAND : null;
	}

	/**
	 * Draws the pose: {@code arms} once per arm (null for an invisible player), then {@code tablet} with the stack
	 * set up so the item model, drawn with no display transform, has its screen (the model's south face) towards the
	 * eye and its centre on the pose's origin.
	 */
	public static void submit(PoseStack pose, float raise, @Nullable Consumer<HumanoidArm> arms, Runnable tablet) {
		submit(pose, raise, Util.getMillis(), arms, tablet);
	}

	/**
	 * The pose at {@code nowMs}. On the way up the tablet comes in turned a little and squares up as it arrives, the
	 * way a device is pulled out and brought to the eyes; held up, it sways by a hair with the breath, so it reads as
	 * held rather than pinned to the view.
	 */
	static void submit(PoseStack pose, float raise, long nowMs, @Nullable Consumer<HumanoidArm> arms,
			Runnable tablet) {
		float lowered = 1.0f - Math.clamp(raise, 0.0f, 1.0f);
		float breath = (float) Math.sin(nowMs / 1000.0 * Math.PI * 0.5) * raise;
		pose.pushPose();
		pose.translate(0.0f, ReactorTabletLayout.RAISED_LIFT - 1.2f * lowered - 0.5f * lowered + 0.004f * breath,
				-ReactorTabletLayout.RAISED_DISTANCE);
		Poses.rotate(pose, Axis.XP.rotationDegrees(-85.0f * lowered));
		Poses.rotate(pose, Axis.YP.rotationDegrees(14.0f * lowered * lowered));
		Poses.rotate(pose, Axis.ZP.rotationDegrees(-9.0f * lowered + 0.4f * breath));
		if (arms != null) {
			pose.pushPose();
			Poses.rotate(pose, Axis.YP.rotationDegrees(90.0f));
			arms.accept(HumanoidArm.RIGHT);
			arms.accept(HumanoidArm.LEFT);
			pose.popPose();
		}
		float scale = 2.0f * ReactorTabletLayout.MODEL_SCALE;
		pose.scale(scale, scale, scale);
		// No centring shift here: ItemTransform.apply moves the model by −0.5 itself, NONE context included.
		tablet.run();
		pose.popPose();
	}
}

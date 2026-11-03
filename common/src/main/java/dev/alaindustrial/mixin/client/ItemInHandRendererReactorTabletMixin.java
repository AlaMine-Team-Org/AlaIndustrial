package dev.alaindustrial.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.alaindustrial.client.tablet.ReactorTabletHands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The reactor tablet held in both hands while it is lifted, open or being lowered (MOD-946). Outside that animation
 * this mixin changes nothing.
 *
 * <p>During it, the call for the hand that holds the tablet draws the whole pose — vanilla's two map arms and the
 * tablet's model — through {@link ReactorTabletHands}, and the call for the other hand draws nothing, because both
 * hands are on the tablet. It sits at the head of {@code submitArmWithItem}, before vanilla's map test.
 *
 * <p><b>26.2 names the class {@link ItemInHandRenderer}</b> and its {@code renderMapHand} takes no player state
 * (javap, 26.2); 26.3's twin targets {@code FirstPersonHandsAndItemsRenderer}.
 *
 * <p>Cosmetic by nature, so it lives in the optional config: a conflict costs the pose, never the game.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererReactorTabletMixin {

	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	private ItemStack mainHandItem;

	@Shadow
	private ItemStack offHandItem;

	/** The tablet's model, resolved with no display transform; reused every frame. */
	@Unique
	private final ItemStackRenderState alaindustrial$tablet = new ItemStackRenderState();

	@Shadow
	private void renderMapHand(PoseStack poseStack, SubmitNodeCollector collector, int light, HumanoidArm arm) {
		throw new AssertionError();
	}

	@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
	private void alaindustrial$reactorTablet(AbstractClientPlayer player, float partialTicks, float xRot,
			InteractionHand hand, float attack, ItemStack stack, float inverseArmHeight, PoseStack poseStack,
			SubmitNodeCollector collector, int light, CallbackInfo ci) {
		float raise = ReactorTabletHands.raise();
		if (raise <= 0.0f) {
			return;
		}
		InteractionHand tabletHand = ReactorTabletHands.tabletHand(mainHandItem, offHandItem);
		if (tabletHand == null) {
			return;
		}
		ci.cancel();
		if (hand != tabletHand) {
			return;
		}
		boolean armsShown = !player.isInvisible();
		ReactorTabletHands.submit(poseStack, raise,
				armsShown ? arm -> renderMapHand(poseStack, collector, light, arm) : null,
				() -> {
					minecraft.getItemModelResolver().updateForTopItem(alaindustrial$tablet, stack,
							ItemDisplayContext.NONE, minecraft.level, player, 0);
					alaindustrial$tablet.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
				});
	}
}

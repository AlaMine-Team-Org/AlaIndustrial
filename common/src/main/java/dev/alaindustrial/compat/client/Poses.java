package dev.alaindustrial.compat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Quaternionfc;

/**
 * Pose-stack operations whose name differs between the Minecraft lines (MOD-703, ADR-036). Every line
 * has a twin of this class with the same signatures; only the bodies differ. The block-entity renderers
 * call these instead of the {@link PoseStack} method of their own line, so a renderer's source is the
 * same on every line.
 *
 * <p>Facades whose signatures carry a client type ({@link PoseStack} is one) live in {@code compat.client},
 * the one part of the facade package where {@code ArchitectureRules.clientTypesStayInsideClientPackages}
 * allows client types.
 *
 * <p><b>This twin: Minecraft 26.2</b>, where the quaternion overload is {@code PoseStack.mulPose}.
 */
public final class Poses {

	private Poses() {
	}

	/** Rotates the top of the stack by {@code rotation}. 26.2: {@code PoseStack.mulPose(Quaternionfc)}. */
	public static void rotate(PoseStack poseStack, Quaternionfc rotation) {
		poseStack.mulPose(rotation);
	}
}

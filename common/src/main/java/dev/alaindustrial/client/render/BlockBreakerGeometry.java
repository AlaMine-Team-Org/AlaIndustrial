package dev.alaindustrial.client.render;

/**
 * The Block Breaker's auger (MOD-787) — GENERATED, do not edit by hand.
 *
 * <p>Source: {@code tools/model_sources/block_breaker/build_block_breaker.py}, group {@code bit}, in the
 * block's own pixels, at rest, front facing north. Row layout is the one {@link CubeMesh} reads.
 */
final class BlockBreakerGeometry {

	private BlockBreakerGeometry() {
	}

	static final float[][] BIT = {
			{3.5f, 7f, 1.5f, 12.5f, 9f, 2.5f,
					0f, 0f, 0f, 0f, 0f, 0f,
					0.751953f, 0.001953f, 0.888672f, 0.029297f, 0.751953f, 0.001953f,
					0.763672f, 0.029297f, 0.751953f, 0.001953f, 0.888672f, 0.029297f,
					0.751953f, 0.001953f, 0.763672f, 0.029297f, 0.751953f, 0.001953f,
					0.888672f, 0.013672f, 0.751953f, 0.001953f, 0.888672f, 0.013672f},
			{6.5f, 6.5f, 1f, 9.5f, 9.5f, 2.5f,
					0f, 0f, 0f, 0f, 0f, 0f,
					0.001953f, 0.251953f, 0.044922f, 0.294922f, 0.001953f, 0.251953f,
					0.029297f, 0.294922f, 0.001953f, 0.251953f, 0.044922f, 0.294922f,
					0.001953f, 0.251953f, 0.029297f, 0.294922f, 0.001953f, 0.251953f,
					0.044922f, 0.279297f, 0.001953f, 0.251953f, 0.044922f, 0.279297f},
			{7f, 7f, 0.1f, 9f, 9f, 1f,
					0f, 0f, 0f, 0f, 0f, 0f,
					0.751953f, 0.001953f, 0.779297f, 0.029297f, 0.751953f, 0.001953f,
					0.763672f, 0.029297f, 0.751953f, 0.001953f, 0.779297f, 0.029297f,
					0.751953f, 0.001953f, 0.763672f, 0.029297f, 0.751953f, 0.001953f,
					0.779297f, 0.013672f, 0.751953f, 0.001953f, 0.779297f, 0.013672f},
	};

	/** The point the auger turns about (front axis), in model pixels. */
	static final float PIVOT_X = 8f;
	static final float PIVOT_Y = 8f;
	static final float PIVOT_Z = 1f;

	/** Turns a second while the machine breaks. */
	static final float TURNS_PER_SECOND = 1f;
}

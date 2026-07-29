package dev.alaindustrial.client.compat;

import java.util.List;
import java.util.Objects;

/**
 * Loader-neutral, ordered view of a fluid-input recipe with one or two simultaneous fluid outputs.
 *
 * <p>{@code acceptedInputs} are alternatives for the one input slot. Each {@link Output}, however,
 * is a separate output slot; combining them into one cycling ingredient would incorrectly present
 * simultaneous products as alternatives.
 */
public record FluidOutputViewerModel<F>(
		List<F> acceptedInputs,
		int inputAmountMb,
		List<Output<F>> outputs,
		int energy,
		int processingTicks) {

	public record Output<F>(F fluid, int amountMb) {
		public Output {
			Objects.requireNonNull(fluid, "fluid");
			if (amountMb <= 0) {
				throw new IllegalArgumentException("fluid output amount must be positive");
			}
		}
	}

	public FluidOutputViewerModel {
		acceptedInputs = List.copyOf(acceptedInputs);
		outputs = List.copyOf(outputs);
		if (acceptedInputs.isEmpty()) {
			throw new IllegalArgumentException("fluid recipe viewer needs at least one accepted input");
		}
		if (outputs.isEmpty() || outputs.size() > 2) {
			throw new IllegalArgumentException("fluid recipe viewer supports 1 or 2 output slots");
		}
		if (inputAmountMb <= 0) {
			throw new IllegalArgumentException("fluid input amount must be positive");
		}
		if (energy <= 0 || processingTicks <= 0) {
			throw new IllegalArgumentException("energy and processing time must be positive");
		}
	}
}

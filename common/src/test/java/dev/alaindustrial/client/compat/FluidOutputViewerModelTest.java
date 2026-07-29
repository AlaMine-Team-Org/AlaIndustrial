package dev.alaindustrial.client.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class FluidOutputViewerModelTest {

	@Test
	void preservesInputAlternativesAndSeparateOrderedOutputs() {
		FluidOutputViewerModel<String> model = new FluidOutputViewerModel<>(
				List.of("oil", "foreign_oil"),
				1000,
				List.of(
						new FluidOutputViewerModel.Output<>("diesel", 700),
						new FluidOutputViewerModel.Output<>("heavy_oil", 200)),
				400,
				200);

		assertEquals(List.of("oil", "foreign_oil"), model.acceptedInputs());
		assertEquals("diesel", model.outputs().get(0).fluid());
		assertEquals(700, model.outputs().get(0).amountMb());
		assertEquals("heavy_oil", model.outputs().get(1).fluid());
		assertEquals(200, model.outputs().get(1).amountMb());
	}

	@Test
	void rejectsShapesThatTheViewerCannotRepresent() {
		assertThrows(IllegalArgumentException.class, () -> new FluidOutputViewerModel<>(
				List.of("oil"), 1000, List.of(), 400, 200));
		assertThrows(IllegalArgumentException.class, () -> new FluidOutputViewerModel<>(
				List.of("oil"), 1000,
				List.of(
						new FluidOutputViewerModel.Output<>("a", 1),
						new FluidOutputViewerModel.Output<>("b", 1),
						new FluidOutputViewerModel.Output<>("c", 1)),
				400, 200));
		assertThrows(IllegalArgumentException.class, () ->
				new FluidOutputViewerModel.Output<>("diesel", 0));
	}
}

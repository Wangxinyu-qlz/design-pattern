package chain.pipeline;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class CalculateSubtotalStage implements PipelineStage<OrderProcess> {
	@Override
	public String name() {
		return "calculate-subtotal";
	}

	@Override
	public OrderProcess process(OrderProcess input, PipelineContext context) {
		BigDecimal subtotal = input.unitPrice()
				.multiply(BigDecimal.valueOf(input.quantity()))
				.setScale(2, RoundingMode.HALF_UP);
		context.addTrace(name(), "subtotal=" + subtotal);
		return input.withSubtotal(subtotal);
	}
}

package chain.pipeline;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class CalculateTaxStage implements PipelineStage<OrderProcess> {
	private static final BigDecimal TAX_RATE = new BigDecimal("0.06");

	@Override
	public String name() {
		return "calculate-tax";
	}

	@Override
	public OrderProcess process(OrderProcess input, PipelineContext context) {
		BigDecimal taxableAmount = input.subtotal().subtract(input.discount());
		BigDecimal tax = taxableAmount
				.multiply(TAX_RATE)
				.setScale(2, RoundingMode.HALF_UP);
		context.addTrace(name(), "tax=" + tax);
		return input.withTax(tax);
	}
}

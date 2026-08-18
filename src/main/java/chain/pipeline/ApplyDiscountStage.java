package chain.pipeline;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class ApplyDiscountStage implements PipelineStage<OrderProcess> {
	private static final BigDecimal THRESHOLD = new BigDecimal("100.00");
	private static final BigDecimal DISCOUNT_RATE = new BigDecimal("0.10");

	@Override
	public String name() {
		return "apply-discount";
	}

	@Override
	public OrderProcess process(OrderProcess input, PipelineContext context) {
		BigDecimal discount = BigDecimal.ZERO;
		if(input.subtotal().compareTo(THRESHOLD) >= 0) {
			discount = input.subtotal().multiply(DISCOUNT_RATE);
		}
		discount = discount.setScale(2, RoundingMode.HALF_UP);
		context.addTrace(name(), "discount=" + discount);
		return input.withDiscount(discount);
	}
}

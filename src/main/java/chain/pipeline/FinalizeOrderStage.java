package chain.pipeline;

import java.math.BigDecimal;

public class FinalizeOrderStage implements PipelineStage<OrderProcess> {
	@Override
	public String name() {
		return "finalize-order";
	}

	@Override
	public OrderProcess process(OrderProcess input, PipelineContext context) {
		BigDecimal payable = input.subtotal()
				.subtract(input.discount())
				.add(input.tax());
		context.addTrace(name(), "payable=" + payable);
		return input.complete(payable);
	}
}

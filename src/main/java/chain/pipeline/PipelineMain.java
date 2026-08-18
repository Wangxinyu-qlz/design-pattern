package chain.pipeline;

import java.math.BigDecimal;

public class PipelineMain {
	public static void main(String[] args) {
		ProcessingPipeline<OrderProcess> pipeline = new ProcessingPipeline<OrderProcess>()
				.addLast(new CalculateSubtotalStage())
				.addLast(new ApplyDiscountStage())
				.addLast(new CalculateTaxStage())
				.addLast(new FinalizeOrderStage());

		OrderProcess order = OrderProcess.draft(
				"ORDER-001",
				new BigDecimal("68.00"),
				2);

		PipelineResult<OrderProcess> result = pipeline.execute(order);
		result.trace().forEach(System.out::println);

		OrderProcess completed = result.value();
		System.out.println("orderNo=" + completed.orderNo());
		System.out.println("subtotal=" + completed.subtotal());
		System.out.println("discount=" + completed.discount());
		System.out.println("tax=" + completed.tax());
		System.out.println("payable=" + completed.payable());
		System.out.println("status=" + completed.status());
	}
}

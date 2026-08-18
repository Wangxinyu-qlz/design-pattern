package chain.pipeline;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 在流水线中逐步补全金额信息的不可变订单对象。
 */
public record OrderProcess(
		String orderNo,
		BigDecimal unitPrice,
		int quantity,
		BigDecimal subtotal,
		BigDecimal discount,
		BigDecimal tax,
		BigDecimal payable,
		String status
) {
	public OrderProcess {
		Objects.requireNonNull(orderNo, "orderNo");
		Objects.requireNonNull(unitPrice, "unitPrice");
		Objects.requireNonNull(subtotal, "subtotal");
		Objects.requireNonNull(discount, "discount");
		Objects.requireNonNull(tax, "tax");
		Objects.requireNonNull(payable, "payable");
		Objects.requireNonNull(status, "status");
		if(quantity <= 0) {
			throw new IllegalArgumentException("quantity must be greater than zero");
		}
	}

	public static OrderProcess draft(String orderNo, BigDecimal unitPrice, int quantity) {
		BigDecimal zero = money(BigDecimal.ZERO);
		return new OrderProcess(
				orderNo,
				money(unitPrice),
				quantity,
				zero,
				zero,
				zero,
				zero,
				"DRAFT");
	}

	public OrderProcess withSubtotal(BigDecimal value) {
		return new OrderProcess(
				orderNo, unitPrice, quantity, money(value), discount, tax, payable, status);
	}

	public OrderProcess withDiscount(BigDecimal value) {
		return new OrderProcess(
				orderNo, unitPrice, quantity, subtotal, money(value), tax, payable, status);
	}

	public OrderProcess withTax(BigDecimal value) {
		return new OrderProcess(
				orderNo, unitPrice, quantity, subtotal, discount, money(value), payable, status);
	}

	public OrderProcess complete(BigDecimal value) {
		return new OrderProcess(
				orderNo, unitPrice, quantity, subtotal, discount, tax,
				money(value), "READY_TO_PAY");
	}

	private static BigDecimal money(BigDecimal value) {
		return value.setScale(2, RoundingMode.HALF_UP);
	}
}

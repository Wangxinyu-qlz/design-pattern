package chain.validator;

import chain.annotation.Order;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @program: study
 * @author: Qiaolezi
 * @create: 2026-08-18 11:31
 * @description:
 **/
public class ValidatorHandlerChain {

	private final List<ValidateHandler> handlers = new ArrayList<>();

	public void addLastHandler(ValidateHandler handler) {
		handlers.add(handler);
		handlers.sort(Comparator.comparingInt(this::getOrder));
	}

	private int getOrder(ValidateHandler handler) {
		Order order = handler.getClass().getAnnotation(Order.class);
		if(order == null) {
			throw new IllegalArgumentException(
					"Missing @Order on " + handler.getClass().getName());
		}
		return order.value();
	}

	public void validate(Object value, ValidationContext validationContext) {
		ChainExecutionContext context = new ChainExecutionContext(value, validationContext);
		while(true) {
			int index = context.getCurrentIndex();
			if(index == handlers.size()) {
				break;
			}

			ValidateHandler handler = handlers.get(index);
			handler.validate(context.getValue(), context);

			// 说明当前handler没有调用doNext()，索引未增加
			if(index == context.getCurrentIndex()) {
				break;
			}

		}
	}
}

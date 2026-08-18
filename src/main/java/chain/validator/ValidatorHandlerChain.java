package chain.validator;

import java.util.ArrayList;
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
	}

	public void validate(Object bean) {
		for (ValidateHandler handler : handlers) {
			handler.validate(bean);
		}
	}
}

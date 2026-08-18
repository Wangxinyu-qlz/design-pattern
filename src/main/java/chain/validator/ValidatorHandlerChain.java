package chain.validator;

import chain.exception.ValidateException;

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
		List<String> errorMessages = new ArrayList<>();
		for (ValidateHandler handler : handlers) {
			try {
				handler.validate(bean);
			} catch (ValidateException e) {
				errorMessages.add(e.getMessage());
			}
		}
		if(errorMessages.isEmpty()) {
			return;
		}
		throw new ValidateException(String.join(",", errorMessages));
	}
}

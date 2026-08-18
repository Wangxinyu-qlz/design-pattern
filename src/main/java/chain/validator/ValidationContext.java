package chain.validator;

import chain.exception.ValidateException;

import java.util.ArrayList;
import java.util.List;

/**
 * 保存整个对象校验过程中的结果。
 */
public class ValidationContext {
	private final List<String> errorMessages = new ArrayList<>();

	public void appendErrorMessage(String errorMessage) {
		errorMessages.add(errorMessage);
	}

	public void throwExceptionIfNecessary() throws ValidateException {
		if(errorMessages.isEmpty()) {
			return;
		}
		throw new ValidateException(String.join(";", errorMessages));
	}
}

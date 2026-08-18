package chain.validator;

import chain.exception.ValidateException;

import java.util.ArrayList;
import java.util.List;

/**
 * @program: study
 * @author: Qiaolezi
 * @create: 2026-08-18 12:08
 * @description:
 **/
public class ValidatorContext {
	private final List<String> errorMessages = new ArrayList<>();

	private boolean shouldStop = false;

	public void appendErrorMessage(String errorMessage) {
		errorMessages.add(errorMessage);
	}

	public void throwExceptionIfNecessary() throws ValidateException {
		if(errorMessages.isEmpty()) {
			return;
		}
		throw new ValidateException(String.join(";", errorMessages));
	}

	public boolean shouldStop() {
		return shouldStop;
	}

	public void stopChain() {
		this.shouldStop = true;
	}
}

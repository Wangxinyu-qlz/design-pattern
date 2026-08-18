package chain.validator;

import chain.exception.ValidateException;

/**
 * @program: study
 * @author: Qiaolezi
 * @create: 2026-08-18 10:50
 * @description:
 **/
public class MaxValidateHandler implements ValidateHandler {
	private final int max;

	public MaxValidateHandler(int max) {
		this.max = max;
	}

	@Override
	public void validate(Object value, ValidatorContext context) throws ValidateException {
		if(value instanceof Integer intVal) {
			if(intVal > max) {
				context.appendErrorMessage("值为" + intVal + "不能大于" + max);
				context.stopChain();
			}
		}
	}
}

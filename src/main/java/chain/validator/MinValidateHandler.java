package chain.validator;

import chain.exception.ValidateException;
import org.springframework.core.annotation.Order;

/**
 * @program: study
 * @author: Qiaolezi
 * @create: 2026-08-18 10:50
 * @description:
 **/
@Order(20)
public class MinValidateHandler implements ValidateHandler {
	private final int min;

	public MinValidateHandler(int min) {
		this.min = min;
	}

	@Override
	public void validate(Object value, ChainExecutionContext context) throws ValidateException {
		if(value instanceof Integer intVal) {
			if(intVal < min) {
				context.appendErrorMessage("值为" + intVal + "不能小于" + min);
			}
			context.doNext(value);
		}
	}
}

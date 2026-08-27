package chain.validator;

import chain.exception.ValidateException;
import org.springframework.core.annotation.Order;

/**
 * @program: study
 * @author: Qiaolezi
 * @create: 2026-08-18 10:50
 * @description:
 **/
@Order(30)
public class LengthValidateHandler implements ValidateHandler {
	private final int length;

	public LengthValidateHandler(int length) {
		this.length = length;
	}

	@Override
	public void validate(Object value, ChainExecutionContext context) throws ValidateException {
		if(value instanceof String StrVal) {
			if(StrVal.length() > length) {
				context.appendErrorMessage("长度为" + StrVal.length() + "不能大于" + length);
			}
			context.doNext(value);
		}
	}
}

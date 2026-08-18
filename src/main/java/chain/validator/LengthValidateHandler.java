package chain.validator;

import chain.exception.ValidateException;

/**
 * @program: study
 * @author: Qiaolezi
 * @create: 2026-08-18 10:50
 * @description:
 **/
public class LengthValidateHandler implements ValidateHandler {
	private final int length;

	public LengthValidateHandler(int length) {
		this.length = length;
	}

	@Override
	public void validate(Object value) throws ValidateException {
		if(value instanceof String StrVal) {
			if(StrVal.length() > length)
				throw new ValidateException("长度为" + StrVal.length() + "不能大于" + length);
		}
	}
}
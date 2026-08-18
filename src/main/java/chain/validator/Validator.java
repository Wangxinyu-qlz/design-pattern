package chain.validator;

import chain.annotation.Length;
import chain.annotation.Max;
import chain.annotation.Min;
import chain.exception.ValidateException;

import java.lang.reflect.Field;

/**
 * @program: study
 * @author: Qiaolezi
 * @create: 2026-08-11 16:54
 * @description:
 **/
public class Validator {

	public void validate(Object bean) throws ValidateException, IllegalAccessException {
		Class<?> beanClass = bean.getClass();
		Field[] declaredFields = beanClass.getDeclaredFields();
		for (Field field : declaredFields) {
			field.setAccessible(true);
			Object value = field.get(bean);
			validateMax(field, value);
			validateMin(field, value);
			validateLength(field, value);
		}
	}

	private void validateMax(Field field, Object value) throws ValidateException {
		Max max = field.getAnnotation(Max.class);
		if(value instanceof Integer intValue) {
			if(max.value() < intValue) {
				throw new ValidateException("值为" + value + "不能大于" + max.value());
			}
		}
	}

	private void validateMin(Field field, Object value) throws ValidateException {
		Min min = field.getAnnotation(Min.class);
		if(value instanceof Integer) {
			if(min.value() > (Integer) value) {
				throw new ValidateException("值为" + value + "不能小于" + min.value());
			}
		}
	}

	private void validateLength(Field field, Object value) throws ValidateException {
		Length length = field.getAnnotation(Length.class);
		if(value instanceof String) {
			if(length.value() < ((String) value).length()) {
				throw new ValidateException("长度为" + ((String) value).length() + "不能大于" + length.value());
			}
		}
	}
}

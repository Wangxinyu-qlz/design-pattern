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
			Max max = field.getAnnotation(Max.class);
			if(max != null) {
				new MaxValidateHandler(max.value()).validate(value);
			}
			Min min = field.getAnnotation(Min.class);
			if(min != null) {
				new MinValidateHandler(min.value()).validate(value);
			}
			Length length = field.getAnnotation(Length.class);
			if(length != null) {
				new LengthValidateHandler(length.value()).validate(value);
			}
		}
	}
}

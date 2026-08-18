package chain.validator;

import chain.annotation.Length;
import chain.annotation.Max;
import chain.annotation.Min;
import chain.exception.ValidateException;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

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
			ValidatorHandlerChain chain = buildHandlerChain(field);
			chain.validate(field.get(bean));
		}
	}

	// 可以使用享元模式/工厂模式等进行优化
	private ValidatorHandlerChain buildHandlerChain(Field field) {
		ValidatorHandlerChain chain = new ValidatorHandlerChain();
		Max max = field.getAnnotation(Max.class);
		if(max != null) {
			chain.addLastHandler(new MaxValidateHandler(max.value()));
		}
		Min min = field.getAnnotation(Min.class);
		if(min != null) {
			chain.addLastHandler(new MinValidateHandler(min.value()));
		}
		Length length = field.getAnnotation(Length.class);
		if(length != null) {
			chain.addLastHandler(new LengthValidateHandler(length.value()));
		}
		return chain;
	}
}

package chain.validator;

import chain.exception.ValidateException;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Field;
import java.util.List;

/**
 * @program: study
 * @author: Qiaolezi
 * @create: 2026-08-11 16:54
 * @description:
 **/
public class Validator {
	private final List<ValidateHandlerFactory> handlerFactories;

	public Validator() {
		this(List.of(new MaxValidateHandlerFactory(), new MinValidateHandlerFactory(),
				new LengthValidateHandlerFactory()));
	}

	@Autowired
	public Validator(List<ValidateHandlerFactory> handlerFactories) {
		this.handlerFactories = List.copyOf(handlerFactories);
	}

	public void validate(Object bean) throws ValidateException, IllegalAccessException {
		Class<?> beanClass = bean.getClass();
		Field[] declaredFields = beanClass.getDeclaredFields();
		ValidationContext validationContext = new ValidationContext();
		for (Field field : declaredFields) {
			field.setAccessible(true);
			ValidatorHandlerChain chain = buildHandlerChain(field);
			chain.validate(field.get(bean), validationContext);
		}

		validationContext.throwExceptionIfNecessary();
	}

	// 可以使用享元模式/工厂模式等进行优化
	private ValidatorHandlerChain buildHandlerChain(Field field) {
		List<ValidateHandler> handlers = handlerFactories.stream()
				.filter(factory -> factory.supports(field))
				.map(factory -> factory.create(field))
				.toList();
		ValidatorHandlerChain chain = new ValidatorHandlerChain(handlers);
		return chain;
	}
}

package chain.validator;

import chain.annotation.Min;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;

@Component
@Order(20)
public class MinValidateHandlerFactory implements ValidateHandlerFactory {
    @Override
    public boolean supports(Field field) {
        return field.isAnnotationPresent(Min.class);
    }

    @Override
    public ValidateHandler create(Field field) {
        return new MinValidateHandler(field.getAnnotation(Min.class).value());
    }
}

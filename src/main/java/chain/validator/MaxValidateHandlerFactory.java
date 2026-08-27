package chain.validator;

import chain.annotation.Max;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;

@Component
@Order(10)
public class MaxValidateHandlerFactory implements ValidateHandlerFactory {
    @Override
    public boolean supports(Field field) {
        return field.isAnnotationPresent(Max.class);
    }

    @Override
    public ValidateHandler create(Field field) {
        return new MaxValidateHandler(field.getAnnotation(Max.class).value());
    }
}

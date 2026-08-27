package chain.validator;

import chain.annotation.Length;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;

@Component
@Order(30)
public class LengthValidateHandlerFactory implements ValidateHandlerFactory {
    @Override
    public boolean supports(Field field) {
        return field.isAnnotationPresent(Length.class);
    }

    @Override
    public ValidateHandler create(Field field) {
        return new LengthValidateHandler(field.getAnnotation(Length.class).value());
    }
}

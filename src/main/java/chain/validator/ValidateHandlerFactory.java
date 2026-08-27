package chain.validator;

import java.lang.reflect.Field;

public interface ValidateHandlerFactory {
    boolean supports(Field field);

    ValidateHandler create(Field field);
}

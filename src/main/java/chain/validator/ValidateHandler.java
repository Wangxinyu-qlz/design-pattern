package chain.validator;

/**
 * @program: study
 * @author: Qiaolezi
 * @create: 2026-08-18 10:48
 * @description:
 **/
public interface ValidateHandler {
	void validate(Object value, ValidatorContext context);
}

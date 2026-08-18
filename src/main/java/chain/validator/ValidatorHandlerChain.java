package chain.validator;

import java.util.ArrayList;
import java.util.List;

/**
 * @program: study
 * @author: Qiaolezi
 * @create: 2026-08-18 11:31
 * @description:
 **/
public class ValidatorHandlerChain {

	private final List<ValidateHandler> handlers = new ArrayList<>();

	public void addLastHandler(ValidateHandler handler) {
		handlers.add(handler);
	}

	public void validate(Object value) {
		ValidatorContext context = new ValidatorContext(value);
		while(true) {
			int index = context.getCurrentIndex();
			if(index == handlers.size()) {
				break;
			}

			ValidateHandler handler = handlers.get(index);
			handler.validate(context.getValue(), context);

			// 说明当前handler没有调用doNext()，索引未增加
			if(index == context.getCurrentIndex()) {
				break;
			}

		}
		context.throwExceptionIfNecessary();
	}
}

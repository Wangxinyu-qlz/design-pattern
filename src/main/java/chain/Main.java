package chain;

import chain.dto.User;
import chain.validator.Validator;

/**
 * @program: study
 * @author: Qiaolezi
 * @create: 2026-08-11 16:16
 * @description:
 **/
public class Main {
	public static void main(String[] args) throws IllegalAccessException {
		User qiaolezi = new User(18,"qiaolezi");
		new Validator().validate(qiaolezi);
	}
}

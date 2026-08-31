package strategy.service;

import strategy.annotation.SupportUserType;
import strategy.enums.UserTypeEnum;

/**
 * @program: study
 * @author: Qiaolezi
 * @create: 2026-08-27 14:52
 * @description:
 **/
public interface CustomerService {
	default UserTypeEnum getUserTypeFromService() {
		return this.getClass().getAnnotation(SupportUserType.class).value();
	}

	String getCustomer();
}

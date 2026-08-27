package strategy.service;

import strategy.enums.UserTypeEnum;

/**
 * @program: study
 * @author: Qiaolezi
 * @create: 2026-08-27 14:52
 * @description:
 **/
public interface CustomerService {
	UserTypeEnum support();

	String getCustomer();
}

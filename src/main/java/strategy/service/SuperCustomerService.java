package strategy.service;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import strategy.enums.UserTypeEnum;

/**
 * @program: study
 * @author: Qiaolezi
 * @create: 2026-08-27 14:53
 * @description:
 **/
@Order(1)
@Component
public class SuperCustomerService implements CustomerService {

	@Override
	public UserTypeEnum support() {
		return UserTypeEnum.SUPER;
	}

	@Override
	public String getCustomer() {
		return "Super Customer";
	}
}

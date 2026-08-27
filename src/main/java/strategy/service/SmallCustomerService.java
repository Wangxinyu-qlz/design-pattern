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
public class SmallCustomerService implements CustomerService {

	@Override
	public UserTypeEnum support() {
		return UserTypeEnum.SMALL;
	}

	@Override
	public String getCustomer() {
		return "Small Customer";
	}
}

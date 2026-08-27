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
@Order(2)
@Component
public class DefaultCustomerService implements CustomerService {

	@Override
	public UserTypeEnum support() {
		return null;
	}

	@Override
	public String getCustomer() {
		return "default Customer";
	}
}

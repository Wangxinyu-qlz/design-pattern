package strategy.service;

import org.springframework.stereotype.Component;
import strategy.enums.UserTypeEnum;

/**
 * @program: study
 * @author: Qiaolezi
 * @create: 2026-08-27 14:53
 * @description:
 **/
@Component
public class NormalCustomerService implements CustomerService {

	@Override
	public UserTypeEnum support() {
		return UserTypeEnum.NORMAL;
	}

	@Override
	public String getCustomer() {
		return "Normal Customer";
	}
}

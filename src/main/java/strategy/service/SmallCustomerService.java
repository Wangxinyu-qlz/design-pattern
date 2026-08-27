package strategy.service;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

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
	public boolean support(Integer recharge) {
		return recharge > 0 && recharge < 10;
	}

	@Override
	public String getCustomer() {
		return "Small Customer";
	}
}

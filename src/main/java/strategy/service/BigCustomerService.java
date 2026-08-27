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
public class BigCustomerService implements CustomerService {

	@Override
	public boolean support(Integer recharge) {
		return recharge >= 100 && recharge < 10000;
	}

	@Override
	public String getCustomer() {
		return "Big Customer";
	}
}

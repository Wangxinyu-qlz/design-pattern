package strategy.service;

import org.springframework.stereotype.Component;

/**
 * @program: study
 * @author: Qiaolezi
 * @create: 2026-08-27 14:53
 * @description:
 **/
@Component
public class SuperCustomerService implements CustomerService {
	@Override
	public boolean support(Integer recharge) {
		return recharge >= 10000 && recharge < 100_0000;
	}

	@Override
	public String getCustomer() {
		return "Super Customer";
	}
}

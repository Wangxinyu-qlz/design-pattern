package strategy.service;

import org.springframework.stereotype.Component;

/**
 * @program: study
 * @author: Qiaolezi
 * @create: 2026-08-27 14:53
 * @description:
 **/
@Component
public class NormalCustomerService implements CustomerService {
	@Override
	public boolean support(Integer recharge) {
		return recharge >= 10 && recharge < 100;
	}

	@Override
	public String getCustomer() {
		return "Normal Customer";
	}
}

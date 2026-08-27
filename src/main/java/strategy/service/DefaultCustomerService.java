package strategy.service;

import org.springframework.stereotype.Component;

/**
 * @program: study
 * @author: Qiaolezi
 * @create: 2026-08-27 14:53
 * @description:
 **/
@Component
public class DefaultCustomerService implements CustomerService {

	@Override
	public String getCustomer() {
		return "default Customer";
	}
}

package strategy.service;

/**
 * @program: study
 * @author: Qiaolezi
 * @create: 2026-08-27 14:53
 * @description:
 **/
public class SuperCustomerService implements CustomerService {

	@Override
	public String getCustomer() {
		return "Super Customer";
	}
}

package strategy.service;

import org.springframework.stereotype.Component;
import strategy.annotation.SupportUserType;
import strategy.enums.UserTypeEnum;

/**
 * @program: study
 * @author: Qiaolezi
 * @create: 2026-08-27 14:53
 * @description:
 **/
@Component
@SupportUserType(UserTypeEnum.BIG)
public class BigCustomerService implements CustomerService {

	@Override
	public String getCustomer() {
		return "Big Customer";
	}
}

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
@SupportUserType(UserTypeEnum.SMALL)
public class SmallCustomerService implements CustomerService {

	@Override
	public String getCustomer() {
		return "Small Customer";
	}
}

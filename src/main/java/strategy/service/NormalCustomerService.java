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
@SupportUserType(UserTypeEnum.NORMAL)
public class NormalCustomerService implements CustomerService {

	@Override
	public String getCustomer() {
		return "Normal Customer";
	}
}

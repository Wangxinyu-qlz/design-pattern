package strategy.service;

import org.springframework.stereotype.Component;
import strategy.enums.UserTypeEnum;

/**
 * @program: study
 * @author: Qiaolezi
 * @create: 2026-08-27 14:53
 * @description: 如果需要添加策略，只需要实现CustomerService接口，通过@Component注入到容器中，然后分别实现support和getCustomer方法
 **/
@Component
public class PersonalCustomerService implements CustomerService {

	@Override
	public UserTypeEnum support() {
		return UserTypeEnum.PERSONAL;
	}

	@Override
	public String getCustomer() {
		return "Personal Customer";
	}
}

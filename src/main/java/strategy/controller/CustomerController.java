package strategy.controller;

import chain.exception.ValidateException;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import strategy.enums.UserTypeEnum;
import strategy.service.CustomerService;

import java.util.List;

/**
 * @program: study
 * @author: Qiaolezi
 * @create: 2026-08-27 14:43
 * @description:
 **/
@RestController
public class CustomerController {

	@Resource
	private List<CustomerService> customerServices;

	@GetMapping("/customer/{recharge}")
	public String customer(@PathVariable Integer recharge) {
		UserTypeEnum userTypeEnum = analysisUserType(recharge);
		for(CustomerService customerService : customerServices) {
			if(customerService.support().equals(userTypeEnum)) {
				return customerService.getCustomer();
			}
		}
		throw new ValidateException("No customer found for recharge: " + recharge);
	}


	// 聚合所有条件
	private static UserTypeEnum analysisUserType(Integer recharge) {
		if(recharge > 0 && recharge < 10) {
			return UserTypeEnum.SMALL;
		}
		if(recharge >= 10 && recharge < 100) {
			return UserTypeEnum.NORMAL;
		}
		if(recharge >= 100 && recharge < 10000) {
			return UserTypeEnum.BIG;
		}
		if(recharge >= 10000 && recharge < 100_0000) {
			return UserTypeEnum.SUPER;
		}
		if(recharge >= 100_0000) {
			return UserTypeEnum.PERSONAL;
		}
		return null;
	}
}

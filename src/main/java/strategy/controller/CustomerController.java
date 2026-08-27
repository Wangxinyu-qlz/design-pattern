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
		UserTypeEnum userTypeEnum = UserTypeEnum.typeOf(recharge);
		for(CustomerService customerService : customerServices) {
			if(customerService.support().equals(userTypeEnum)) {
				return customerService.getCustomer();
			}
		}
		throw new ValidateException("No customer found for recharge: " + recharge);
	}
}

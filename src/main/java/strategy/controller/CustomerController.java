package strategy.controller;

import chain.exception.ValidateException;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
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
		// 根据条件选择策略
		// 条件本身就是有意义的，并且是和策略严格绑定的，所以可以封装在策略类中
		for(CustomerService customerService : customerServices) {
			if(customerService.support(recharge)) {
				return customerService.getCustomer();
			}
		}
		throw new ValidateException("No customer found for recharge: " + recharge);
	}
}

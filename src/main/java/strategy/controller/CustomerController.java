package strategy.controller;

import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import strategy.enums.UserTypeEnum;
import strategy.service.CustomerService;
import strategy.service.DefaultCustomerService;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @program: study
 * @author: Qiaolezi
 * @create: 2026-08-27 14:43
 * @description:
 **/
@RestController
public class CustomerController {

	@Autowired
	private DefaultCustomerService defaultCustomerService;

	private Map<UserTypeEnum, CustomerService> customerServiceMap;

	@GetMapping("/customer/{recharge}")
	public String customer(@PathVariable Integer recharge) {
		UserTypeEnum userTypeEnum = UserTypeEnum.typeOf(recharge);
		CustomerService customerService = customerServiceMap.getOrDefault(userTypeEnum, defaultCustomerService);
		return customerService.getCustomer();
	}

	@Autowired
	public void setCustomerServiceMap(List<CustomerService> customerServices) {
		this.customerServiceMap = customerServices.stream()
				.filter(customerService -> customerService.support()!=null)
				.collect(Collectors.toMap(CustomerService::support, Function.identity()));
		//customerServices.forEach(customerService -> customerServiceMap.put(customerService.support(), customerService));
	}
}

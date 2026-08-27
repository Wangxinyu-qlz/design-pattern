package strategy.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import strategy.service.BigCustomerService;
import strategy.service.NormalCustomerService;
import strategy.service.SmallCustomerService;
import strategy.service.SuperCustomerService;

/**
 * @program: study
 * @author: Qiaolezi
 * @create: 2026-08-27 14:43
 * @description:
 **/
@RestController
public class CustomerController {

	@GetMapping("/customer/{recharge}")
	public String customer(@PathVariable Integer recharge) {
		//将实现逻辑封装在类中
		if(recharge > 0 && recharge < 10) {
			return new SmallCustomerService().getCustomer();
		}
		if(recharge >= 10 && recharge < 100) {
			return new NormalCustomerService().getCustomer();
		}
		if(recharge >= 100 && recharge < 10000) {
			return new BigCustomerService().getCustomer();
		}
		if(recharge >= 10000) {
			return new SuperCustomerService().getCustomer();
		}
		return "未知客服";
	}
}

package strategy.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

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
		if(recharge > 0 && recharge < 10) {
			return "普通客服";
		}
		if(recharge >= 10 && recharge < 100) {
			return "中级客服";
		}
		if(recharge >= 100 && recharge < 10000) {
			return "高级客服";
		}
		if(recharge >= 10000) {
			return "超级客服";
		}
		return "未知客服";
	}
}

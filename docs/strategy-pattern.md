# 从条件分支到策略注册表：策略模式的逐步演进

策略模式经常被描述成“把一组算法封装起来，使它们可以互相替换”。这句话没有错，但真正写代码时，难点通常不在于创建几个实现类，而在于下面几个问题如何逐步解耦：

- 策略的执行逻辑放在哪里；
- 请求参数如何映射成策略；
- Spring 如何发现所有策略；
- 默认策略如何参与选择；
- 策略标识由谁维护；
- 新增用户类型后，系统如何尽早发现遗漏。

本文以客户服务接口为例，演示从条件分支演进到注解驱动策略注册表的全过程。示例接口是 `/customer/{recharge}`，充值金额与用户类型的对应关系如下：

| 充值金额 | 用户类型 | 策略 |
| --- | --- | --- |
| `0 < recharge < 10` | `SMALL` | 小客户 |
| `10 <= recharge < 100` | `NORMAL` | 普通客户 |
| `100 <= recharge < 10000` | `BIG` | 大客户 |
| `10000 <= recharge < 1000000` | `SUPER` | 超级客户 |
| `recharge >= 1000000` | `PERSONAL` | 个人客户 |

`0` 和负数没有对应的业务类型，最终由默认策略处理。

## 起点：Controller直接承担所有分支

Controller既负责判断充值区间，又负责决定返回哪个客户服务。所有变化都集中在一个方法里：

`src/main/java/strategy/controller/CustomerController.java`

```java
package strategy.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

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
```

判断条件、业务响应和策略扩展点全部耦合在Controller中。增加一种客户类型时，必须打开Controller修改分支；如果客户服务的实现变复杂，Controller还会继续膨胀。

## 第一步：先把响应逻辑封装成策略类

把不同客户等级的响应逻辑分别封装到类中。Controller仍然保留区间判断，只是在分支内部创建对应对象并调用统一接口。

### 统一接口

`src/main/java/strategy/service/CustomerService.java`

```java
package strategy.service;

public interface CustomerService {

	String getCustomer();
}
```

### 四个策略实现

它们遵守同一个接口，但各自封装自己的客户响应。

`src/main/java/strategy/service/SmallCustomerService.java`

```java
package strategy.service;

public class SmallCustomerService implements CustomerService {

	@Override
	public String getCustomer() {
		return "Small Customer";
	}
}
```

`src/main/java/strategy/service/NormalCustomerService.java`

```java
package strategy.service;

public class NormalCustomerService implements CustomerService {

	@Override
	public String getCustomer() {
		return "Normal Customer";
	}
}
```

`src/main/java/strategy/service/BigCustomerService.java`

```java
package strategy.service;

public class BigCustomerService implements CustomerService {

	@Override
	public String getCustomer() {
		return "Big Customer";
	}
}
```

`src/main/java/strategy/service/SuperCustomerService.java`

```java
package strategy.service;

public class SuperCustomerService implements CustomerService {

	@Override
	public String getCustomer() {
		return "Super Customer";
	}
}
```

Controller只改变了分支中的实现方式：

```java
@GetMapping("/customer/{recharge}")
public String customer(@PathVariable Integer recharge) {
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
```

这一步已经得到策略类，但还没有完成策略模式最关键的选择解耦：Controller仍然知道每个具体实现类，也仍然维护着全部判断条件。

## 第二步：让策略自己声明是否支持请求

接口新增 `support(Integer recharge)`，Controller不再导入四个具体类，而是遍历 Spring 注入的策略集合。

`CustomerService` 变为：

```java
package strategy.service;

public interface CustomerService {
	boolean support(Integer recharge);

	String getCustomer();
}
```

所有实现都增加 `@Component`，并实现自己的支持条件。以小客户策略为例：

`src/main/java/strategy/service/SmallCustomerService.java`

```java
package strategy.service;

import org.springframework.stereotype.Component;

@Component
public class SmallCustomerService implements CustomerService {
	@Override
	public boolean support(Integer recharge) {
		return recharge > 0 && recharge < 10;
	}

	@Override
	public String getCustomer() {
		return "Small Customer";
	}
}
```

Controller的选择过程变成统一循环：

```java
@RestController
public class CustomerController {

	@Resource
	private List<CustomerService> customerServices;

	@GetMapping("/customer/{recharge}")
	public String customer(@PathVariable Integer recharge) {
		for(CustomerService customerService : customerServices) {
			if(customerService.support(recharge)) {
				return customerService.getCustomer();
			}
		}
		return "未知客服";
	}
}
```

此时新增策略只需要实现 `CustomerService` 并标注 `@Component`，Controller不再感知具体实现类。

比如：当业务增加个人客户等级时，只需要新增一个实现类，实现CustomerService接口，通过@Component注入到容器中，然后分别实现support和getCustomer方法，Controller没有任何改动：

`src/main/java/strategy/service/PersonalCustomerService.java`

```java
package strategy.service;

import org.springframework.stereotype.Component;

@Component
public class PersonalCustomerService implements CustomerService {
	@Override
	public boolean support(Integer recharge) {
		return recharge >= 100_0000;
	}

	@Override
	public String getCustomer() {
		return "Personal Customer";
	}
}
```

## 第三步：引入默认策略和明确的策略顺序

策略集合现在已经由 Spring 管理，但无效充值金额仍然只能返回字符串。下一步引入默认策略：

`src/main/java/strategy/service/DefaultCustomerService.java`

```java
package strategy.service;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(2)
@Component
public class DefaultCustomerService implements CustomerService {
	@Override
	public boolean support(Integer recharge) {
		return true;
	}

	@Override
	public String getCustomer() {
		return "default Customer";
	}
}
```

具体策略标记 `@Order(1)`，默认策略标记 `@Order(2)`。
否则会出现以下现象：BigCustomer之外的策略总是返回默认策略的"default Customer"。通过Debug可以发现Bean的注入**顺序**错误。
![策略的顺序.png](%E7%AD%96%E7%95%A5%E7%9A%84%E9%A1%BA%E5%BA%8F.png)

以小客户策略为例：

```java
@Order(1)
@Component
public class SmallCustomerService implements CustomerService {
	@Override
	public boolean support(Integer recharge) {
		return recharge > 0 && recharge < 10;
	}

	@Override
	public String getCustomer() {
		return "Small Customer";
	}
}
```

Controller仍然遍历策略列表，但找不到具体类型时会走默认策略：

```java
for(CustomerService customerService : customerServices) {
	if(customerService.support(recharge)) {
		return customerService.getCustomer();
	}
}
throw new ValidateException("No customer found for recharge: " + recharge);
```

这里要区分两种兜底：`DefaultCustomerService.support()` 永远返回 `true`，它是业务层面的默认响应；循环末尾的 `ValidateException` 只有在策略列表为空、顺序失效或策略实现不完整时才会触发。默认策略的 `@Order(2)` 保证它排在具体策略之后。

## 第四步：支持条件从布尔值升级为用户类型

布尔值只能回答“支持还是不支持”，不能表达策略支持的具体类型。于是接口把 `support(Integer recharge)` 改成无参数的 `support()`，返回 `UserTypeEnum`：

`src/main/java/strategy/service/CustomerService.java`

```java
package strategy.service;

import strategy.enums.UserTypeEnum;

public interface CustomerService {
	UserTypeEnum support();

	String getCustomer();
}
```

代表实现不再重复充值区间，只声明自己的类型：

```java
@Component
public class SmallCustomerService implements CustomerService {
	@Override
	public UserTypeEnum support() {
		return UserTypeEnum.SMALL;
	}

	@Override
	public String getCustomer() {
		return "Small Customer";
	}
}
```

默认策略没有业务类型，因此返回 `null`：

```java
@Component
public class DefaultCustomerService implements CustomerService {
	@Override
	public UserTypeEnum support() {
		return null;
	}

	@Override
	public String getCustomer() {
		return "default Customer";
	}
}
```

此时控制器先把充值金额聚合成用户类型，再遍历策略并比较策略声明的类型：

`src/main/java/strategy/controller/CustomerController.java`

```java
package strategy.controller;

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
	private UserTypeEnum analysisUserType(Integer recharge) {
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
```

Controller先通过充值金额得到类型，再把类型与策略返回值比较。类型标签和金额判断开始分离，但金额判断暂时仍在Controller私有方法中。

## 第五步：把金额分类规则放入枚举

如果Controller继续保存所有金额区间，条件仍然没有真正集中。下一步让 `UserTypeEnum` 自己保存判断谓词，并提供 `typeOf` 工厂方法：

`src/main/java/strategy/enums/UserTypeEnum.java`

```java
package strategy.enums;

import java.util.function.IntPredicate;

public enum UserTypeEnum {
	SMALL(recharge -> recharge > 0 && recharge < 10),
	NORMAL(recharge -> recharge >= 10 && recharge < 100),
	BIG(recharge -> recharge >= 100 && recharge < 10000),
	SUPER(recharge -> recharge >= 10000 && recharge < 1000000),
	PERSONAL(recharge -> recharge >= 1000000);

	private IntPredicate support;

	UserTypeEnum(IntPredicate support) {
		this.support = support;
	}

	public static UserTypeEnum typeOf(int recharge) {
		for(UserTypeEnum type : values()) {
			if(type.support.test(recharge))
				return type;
		}
		return null;
	}
}
```

Controller删除原来的 `analysisUserType` 方法，只留下类型获取：

```java
UserTypeEnum userTypeEnum = UserTypeEnum.typeOf(recharge);
```

现在金额区间由枚举统一维护，策略实现只关心自己的类型，Controller只负责协调请求、类型和策略。

## 第六步：用 Map 直接定位策略

类型已经是明确的键，继续遍历列表就显得多余。Controller在注入阶段把策略转换为 `Map<UserTypeEnum, CustomerService>`，请求处理变成一次查表：

```java
@Autowired
private DefaultCustomerService defaultCustomerService;

private Map<UserTypeEnum, CustomerService> customerServiceMap;

@GetMapping("/customer/{recharge}")
public String customer(@PathVariable Integer recharge) {
	UserTypeEnum userTypeEnum = UserTypeEnum.typeOf(recharge);
	CustomerService customerService = customerServiceMap
			.getOrDefault(userTypeEnum, defaultCustomerService);
	return customerService.getCustomer();
}

@Autowired
public void setCustomerServiceMap(List<CustomerService> customerServices) {
	this.customerServiceMap = customerServices.stream()
			.filter(customerService -> customerService.support() != null)
			.collect(Collectors.toMap(CustomerService::support, Function.identity()));
}
```

这一变化有两个直接收益：

1. 选择复杂度从逐个调用 `support()` 变成按类型键查找；
2. 默认策略不再混入业务类型映射，而是通过 `getOrDefault` 明确表达兜底关系。

从这里开始，`@Order` 已经不再参与业务选择；它此前解决的是列表遍历时默认策略的相对位置，改成 Map 后自然失去必要性。


## 第七步：增加策略完整性检查

清理顺序注解后，策略注册阶段增加完整性校验，完整的注册方法如下：

```java
@Autowired
public void setCustomerServiceMap(List<CustomerService> customerServices) {
	this.customerServiceMap = customerServices.stream()
			.filter(customerService -> customerService.support() != null)
			.collect(Collectors.toMap(CustomerService::support, Function.identity()));
	if(this.customerServiceMap.size() != UserTypeEnum.values().length) {
		throw new IllegalArgumentException("有用户类型没有对应的策略");
	}
}
```

完整性检查把问题从请求运行期提前到了应用启动期。比如新增 `UserTypeEnum` 常量却忘记新增策略实现，应用启动时就会失败，而不是等真实请求命中缺失分支后才暴露问题。


## 第八步：用类注解声明策略类型

策略类中的 `support()` 只是返回一个固定枚举值，属于元数据而不是执行逻辑。下一步使用类型注解承载这份声明：

`src/main/java/strategy/annotation/SupportUserType.java`

```java
package strategy.annotation;

import strategy.enums.UserTypeEnum;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface SupportUserType {

	UserTypeEnum value();
}
```

策略实现只保留声明和真正的处理逻辑。以大客户策略为例：

`src/main/java/strategy/service/BigCustomerService.java`

```java
package strategy.service;

import org.springframework.stereotype.Component;
import strategy.annotation.SupportUserType;
import strategy.enums.UserTypeEnum;

@Component
@SupportUserType(UserTypeEnum.BIG)
public class BigCustomerService implements CustomerService {

	@Override
	public String getCustomer() {
		return "Big Customer";
	}
}
```

默认策略没有 `@SupportUserType`，因此不会被放入业务类型 Map。注册逻辑通过反射筛选带注解的类：

```java
this.customerServiceMap = customerServices.stream()
		.filter(customerService ->
				customerService.getClass().isAnnotationPresent(SupportUserType.class))
		.collect(Collectors.toMap(this::getUserTypeFromService, Function.identity()));

private UserTypeEnum getUserTypeFromService(CustomerService customerService) {
	return customerService.getClass()
			.getAnnotation(SupportUserType.class)
			.value();
}
```

此时 `CustomerService` 不再需要 `support()` 方法，接口退化为真正的策略行为契约：只要求实现 `getCustomer()`。

## 第九步：把注解读取能力放回策略接口

Controller虽然已经不依赖 `support()`，但仍然保留了一个只为读取注解而存在的私有方法。最后把这个通用能力提升为接口默认方法：

`src/main/java/strategy/service/CustomerService.java`

```java
package strategy.service;

import strategy.annotation.SupportUserType;
import strategy.enums.UserTypeEnum;

public interface CustomerService {
	default UserTypeEnum getUserTypeFromService(CustomerService customerService) {
		return customerService.getClass()
				.getAnnotation(SupportUserType.class)
				.value();
	}

	String getCustomer();
}
```

Controller的注册表达式随之改成：

```java
.filter(customerService ->
		customerService.getClass().isAnnotationPresent(SupportUserType.class))
.collect(Collectors.toMap(
		service -> service.getUserTypeFromService(service),
		Function.identity()));
```

## 第十步：形成最终的请求链路

完成上述演进后，业务结构已经稳定。当前Controller的完整实现如下：

`src/main/java/strategy/controller/CustomerController.java`

```java
package strategy.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import strategy.annotation.SupportUserType;
import strategy.enums.UserTypeEnum;
import strategy.service.CustomerService;
import strategy.service.DefaultCustomerService;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
				.filter(customerService -> customerService.getClass().isAnnotationPresent(SupportUserType.class))
				.collect(Collectors.toMap(service -> service.getUserTypeFromService(service), Function.identity()));
		//customerServices.forEach(customerService -> customerServiceMap.put(customerService.support(), customerService));
		if(this.customerServiceMap.size() != UserTypeEnum.values().length) {
			throw new IllegalArgumentException("有用户类型没有对应的策略");
		}
	}
}
```

请求执行顺序可以概括为：

```text
recharge
   |
   v
UserTypeEnum.typeOf(recharge)
   |
   v
customerServiceMap.getOrDefault(type, defaultCustomerService)
   |
   v
CustomerService.getCustomer()
```

最终的客户策略仍然只是一个声明式策略实现，比如：

```java
@Component
@SupportUserType(UserTypeEnum.BIG)
public class BigCustomerService implements CustomerService {
	@Override
	public String getCustomer() {
		return "Big Customer";
	}
}
```

其他具体客户策略只替换 `@SupportUserType` 的枚举值和 `getCustomer()` 的业务实现；默认策略则不标注 `@SupportUserType`，由Controller显式注入并作为 `getOrDefault` 的第二个参数。

## 最终结构的职责边界

经过这 10 个连续步骤，代码中形成了四个清晰的职责边界：

### `UserTypeEnum`：负责分类

它拥有充值金额到用户类型的全部规则。金额边界只在这里维护，避免Controller和策略类重复书写。

### `CustomerService`：负责策略行为和类型读取能力

接口定义客户服务的共同动作 `getCustomer()`，并通过默认方法提供从实现类读取类型注解的能力。

### 具体策略类：负责业务实现

每个具体策略用 `@SupportUserType` 声明自己负责的用户类型，用 `getCustomer()` 实现对应响应。新增类型时，主要扩展点就是新增一个 `@Component` 策略类。

### `CustomerController`：负责组装和调度

Controller在依赖注入阶段构造策略注册表，在请求阶段完成类型转换、Map 查找和默认策略兜底。它不再实例化具体策略，也不再保存金额区间。

## 演进路线总结

这套变化可以看成逐层减少Controller职责的过程：

```text
Controller知道所有分支
        |
        v
Controller知道所有策略，但不再知道实现逻辑
        |
        v
策略自己声明支持条件
        |
        v
枚举统一管理金额分类
        |
        v
Map 按类型直接定位策略
        |
        v
注解声明策略类型，启动时校验覆盖完整性
```

> 出处：B 站 UP 主 -- 学java的生生 
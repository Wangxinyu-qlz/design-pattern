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

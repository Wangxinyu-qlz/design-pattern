package chain.dto;

import chain.annotation.Length;
import chain.annotation.Max;
import chain.annotation.Min;

public record User(
		@Max(10)
		@Min(30)
		Integer age,
		@Length(4)
		String name
) {
}

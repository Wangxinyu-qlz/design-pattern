package chain.dto;

import chain.annotation.Length;
import chain.annotation.Max;
import chain.annotation.Min;

public record User(
		@Length(4)
		String name,
		@Max(100)
		@Min(1)
		Integer age
) {
}

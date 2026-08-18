package chain.pipeline;

import java.util.List;
import java.util.Objects;

public record PipelineResult<T>(T value, List<String> trace) {
	public PipelineResult {
		Objects.requireNonNull(value, "value");
		trace = List.copyOf(trace);
	}
}

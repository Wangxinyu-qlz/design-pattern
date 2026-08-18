package chain.pipeline;

import java.util.ArrayList;
import java.util.List;

/**
 * 保存一次流水线执行中的公共信息。
 */
public class PipelineContext {
	private final List<String> trace = new ArrayList<>();

	public void addTrace(String stage, String message) {
		trace.add(stage + ": " + message);
	}

	List<String> snapshot() {
		return List.copyOf(trace);
	}
}

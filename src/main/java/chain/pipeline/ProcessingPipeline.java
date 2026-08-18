package chain.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 依次执行全部节点，并把每个节点的输出传给下一个节点。
 */
public class ProcessingPipeline<T> {
	private final List<PipelineStage<T>> stages = new ArrayList<>();

	public ProcessingPipeline<T> addLast(PipelineStage<T> stage) {
		stages.add(Objects.requireNonNull(stage, "stage"));
		return this;
	}

	public PipelineResult<T> execute(T input) {
		T current = Objects.requireNonNull(input, "input");
		PipelineContext context = new PipelineContext();

		for (PipelineStage<T> stage : stages) {
			context.addTrace(stage.name(), "start");
			current = Objects.requireNonNull(
					stage.process(current, context),
					stage.name() + " returned null");
			context.addTrace(stage.name(), "completed");
		}

		return new PipelineResult<>(current, context.snapshot());
	}
}

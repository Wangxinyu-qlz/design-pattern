package chain.pipeline;

/**
 * 流水线节点：接收上一个节点的输出，并返回下一个节点的输入。
 */
public interface PipelineStage<T> {
	String name();

	T process(T input, PipelineContext context);
}

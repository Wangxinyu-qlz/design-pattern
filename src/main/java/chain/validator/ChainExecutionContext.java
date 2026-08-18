package chain.validator;

/**
 * 保存一条字段责任链的执行状态。
 */
public class ChainExecutionContext {
	private final ValidationContext validationContext;

	private boolean shouldStop;

	private int index;

	private Object value;

	public ChainExecutionContext(Object value, ValidationContext validationContext) {
		this.value = value;
		this.validationContext = validationContext;
	}

	public void appendErrorMessage(String errorMessage) {
		validationContext.appendErrorMessage(errorMessage);
	}

	public boolean shouldStop() {
		return shouldStop;
	}

	public void stopChain() {
		this.shouldStop = true;
	}

	public int getCurrentIndex() {
		return index;
	}

	public Object getValue() {
		return value;
	}

	public void doNext(Object value) {
		index++;
		this.value = value;
	}
}

# 从字段校验到责任链：代码如何一步步演进

本文用一个基于注解的 `User` 校验器，完整演示责任链是怎样从固定流程演进出来的。重点不是背诵某个类名，而是观察每次代码变更解决了什么问题：规则如何拆开、节点如何排列、错误如何收集，以及节点如何决定继续还是停止。

示例约束如下：

- `age` 同时有最大值和最小值规则；
- `name` 有长度规则；
- 同一个字段上的多个规则属于同一条链；
- 每个字段单独创建一条链和一个上下文。

## 项目结构

```text
src/main/java/chain/
├── Main.java
├── annotation/
│   ├── Length.java
│   ├── Max.java
│   └── Min.java
├── dto/User.java
├── exception/ValidateException.java
└── validator/
    ├── LengthValidateHandler.java
    ├── MaxValidateHandler.java
    ├── MinValidateHandler.java
    ├── ValidateHandler.java
    ├── Validator.java
    ├── ValidatorContext.java
    └── ValidatorHandlerChain.java
```

下面每个代码块都对应一个完整文件。为了避免重复，阶段中没有变化的文件沿用前一个阶段的完整文件；文章末尾还给出当前版本的全部源码。

## 阶段一：把所有规则写在 Validator 里

先不引入责任链，只完成最小的反射校验闭环。

### `src/main/java/chain/annotation/Length.java`

```java
package chain.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Length {
    int value();
}
```

### `src/main/java/chain/annotation/Max.java`

```java
package chain.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Max {
    int value();
}
```

### `src/main/java/chain/annotation/Min.java`

```java
package chain.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Min {
    int value();
}
```

### `src/main/java/chain/exception/ValidateException.java`

```java
package chain.exception;

public class ValidateException extends RuntimeException {
    public ValidateException(String message) {
        super(message);
    }
}
```

### `src/main/java/chain/dto/User.java`

```java
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
```

### `src/main/java/chain/validator/Validator.java`

```java
package chain.validator;

import chain.annotation.Length;
import chain.annotation.Max;
import chain.annotation.Min;
import chain.exception.ValidateException;

import java.lang.reflect.Field;

public class Validator {

    public void validate(Object bean) throws ValidateException, IllegalAccessException {
        Class<?> beanClass = bean.getClass();
        Field[] declaredFields = beanClass.getDeclaredFields();
        for (Field field : declaredFields) {
            field.setAccessible(true);
            Object value = field.get(bean);
            validateMax(field, value);
            validateMin(field, value);
            validateLength(field, value);
        }
    }

    private void validateMax(Field field, Object value) throws ValidateException {
        Max max = field.getAnnotation(Max.class);
        if (value instanceof Integer intValue) {
            if (max.value() < intValue) {
                throw new ValidateException("值为" + value + "不能大于" + max.value());
            }
        }
    }

    private void validateMin(Field field, Object value) throws ValidateException {
        Min min = field.getAnnotation(Min.class);
        if (value instanceof Integer) {
            if (min.value() > (Integer) value) {
                throw new ValidateException("值为" + value + "不能小于" + min.value());
            }
        }
    }

    private void validateLength(Field field, Object value) throws ValidateException {
        Length length = field.getAnnotation(Length.class);
        if (value instanceof String) {
            if (length.value() < ((String) value).length()) {
                throw new ValidateException(
                        "长度为" + ((String) value).length() + "不能大于" + length.value());
            }
        }
    }
}
```

### `src/main/java/chain/Main.java`

```java
package chain;

import chain.dto.User;
import chain.validator.Validator;

public class Main {
    public static void main(String[] args) throws IllegalAccessException {
        User user = new User("qiaolezi", 18);
        new Validator().validate(user);
    }
}
```

这版代码能完成校验，但 `Validator` 同时承担反射、规则选择、执行顺序和异常控制。增加新规则时，必须继续修改这个类；规则之间也没有统一的处理器协议。

## 阶段二：把每条规则抽成独立处理器

现在把三个私有方法变成三个类。先定义统一入口。

### `src/main/java/chain/validator/ValidateHandler.java`

```java
package chain.validator;

import chain.exception.ValidateException;

public interface ValidateHandler {
    void validate(Object value) throws ValidateException;
}
```

### `src/main/java/chain/validator/MaxValidateHandler.java`

```java
package chain.validator;

import chain.exception.ValidateException;

public class MaxValidateHandler implements ValidateHandler {
    private final int max;

    public MaxValidateHandler(int max) {
        this.max = max;
    }

    @Override
    public void validate(Object value) throws ValidateException {
        if (value instanceof Integer intValue && intValue > max) {
            throw new ValidateException("值为" + intValue + "不能大于" + max);
        }
    }
}
```

### `src/main/java/chain/validator/MinValidateHandler.java`

```java
package chain.validator;

import chain.exception.ValidateException;

public class MinValidateHandler implements ValidateHandler {
    private final int min;

    public MinValidateHandler(int min) {
        this.min = min;
    }

    @Override
    public void validate(Object value) throws ValidateException {
        if (value instanceof Integer intValue && intValue < min) {
            throw new ValidateException("值为" + intValue + "不能小于" + min);
        }
    }
}
```

### `src/main/java/chain/validator/LengthValidateHandler.java`

```java
package chain.validator;

import chain.exception.ValidateException;

public class LengthValidateHandler implements ValidateHandler {
    private final int length;

    public LengthValidateHandler(int length) {
        this.length = length;
    }

    @Override
    public void validate(Object value) throws ValidateException {
        if (value instanceof String stringValue && stringValue.length() > length) {
            throw new ValidateException(
                    "长度为" + stringValue.length() + "不能大于" + length);
        }
    }
}
```

`Validator` 改成根据注解创建处理器：

### `src/main/java/chain/validator/Validator.java`

```java
package chain.validator;

import chain.annotation.Length;
import chain.annotation.Max;
import chain.annotation.Min;
import chain.exception.ValidateException;

import java.lang.reflect.Field;

public class Validator {

    public void validate(Object bean) throws ValidateException, IllegalAccessException {
        for (Field field : bean.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            Object value = field.get(bean);

            Max max = field.getAnnotation(Max.class);
            if (max != null) {
                new MaxValidateHandler(max.value()).validate(value);
            }

            Min min = field.getAnnotation(Min.class);
            if (min != null) {
                new MinValidateHandler(min.value()).validate(value);
            }

            Length length = field.getAnnotation(Length.class);
            if (length != null) {
                new LengthValidateHandler(length.value()).validate(value);
            }
        }
    }
}
```

现在规则本身已经独立，但 `Validator` 还在逐个创建和调用处理器。下一步把这段调度逻辑移到链对象中。

## 阶段三：用链保存并顺序执行处理器

先增加链容器：

### `src/main/java/chain/validator/ValidatorHandlerChain.java`

```java
package chain.validator;

import java.util.ArrayList;
import java.util.List;

public class ValidatorHandlerChain {
    private final List<ValidateHandler> handlers = new ArrayList<>();

    public void addLastHandler(ValidateHandler handler) {
        handlers.add(handler);
    }

    public void validate(Object value) {
        for (ValidateHandler handler : handlers) {
            handler.validate(value);
        }
    }
}
```
用数组还是链表都可以。每个处理器内部保存 next，那就是“链表式责任链”，甚至不需要 List。这种方式适合节点之间直接传递控制权。

```java
interface Handler {
    void setNext(Handler next);

    void handle(Object value);
}
```

`Validator` 只负责组装当前字段的链：

### `src/main/java/chain/validator/Validator.java`

```java
package chain.validator;

import chain.annotation.Length;
import chain.annotation.Max;
import chain.annotation.Min;
import chain.exception.ValidateException;

import java.lang.reflect.Field;

public class Validator {

    public void validate(Object bean) throws ValidateException, IllegalAccessException {
        for (Field field : bean.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            ValidatorHandlerChain chain = buildHandlerChain(field);
            chain.validate(field.get(bean));
        }
    }

    private ValidatorHandlerChain buildHandlerChain(Field field) {
        ValidatorHandlerChain chain = new ValidatorHandlerChain();

        Max max = field.getAnnotation(Max.class);
        if (max != null) {
            chain.addLastHandler(new MaxValidateHandler(max.value()));
        }

        Min min = field.getAnnotation(Min.class);
        if (min != null) {
            chain.addLastHandler(new MinValidateHandler(min.value()));
        }

        Length length = field.getAnnotation(Length.class);
        if (length != null) {
            chain.addLastHandler(new LengthValidateHandler(length.value()));
        }

        return chain;
    }
}
```

为了观察同一字段上的多个节点，把测试数据改成矛盾约束：

### `src/main/java/chain/dto/User.java`

```java
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
```

### `src/main/java/chain/Main.java`

```java
package chain;

import chain.dto.User;
import chain.validator.Validator;

public class Main {
    public static void main(String[] args) throws IllegalAccessException {
        User user = new User(18, "qiaolezi");
        new Validator().validate(user);
    }
}
```

此时链已经存在，但处理器抛出的第一个异常仍会跳出 `for` 循环。要让同一字段上的所有节点都有机会执行，需要集中处理错误。

## 阶段四：在链里收集多个异常

只改链对象，处理器和接口暂时保持不变。

### `src/main/java/chain/validator/ValidatorHandlerChain.java`

```java
package chain.validator;

import chain.exception.ValidateException;

import java.util.ArrayList;
import java.util.List;

public class ValidatorHandlerChain {
    private final List<ValidateHandler> handlers = new ArrayList<>();

    public void addLastHandler(ValidateHandler handler) {
        handlers.add(handler);
    }

    public void validate(Object value) {
        List<String> errorMessages = new ArrayList<>();

        for (ValidateHandler handler : handlers) {
            try {
                handler.validate(value);
            } catch (ValidateException exception) {
                errorMessages.add(exception.getMessage());
            }
        }

        if (!errorMessages.isEmpty()) {
            throw new ValidateException(String.join(",", errorMessages));
        }
    }
}
```

这样 `age = 18` 会同时得到最大值和最小值错误，但这只是一个能工作的过渡版本。它把“校验失败”当成了异常控制流：

```text
执行节点 -> 抛出异常 -> 链捕获异常 -> 保存消息 -> 继续执行
```

实际想表达的业务过程却是：

```text
执行节点 -> 记录校验错误 -> 继续执行
```

这会带来几个问题：

1. 校验失败是预期结果，不一定是系统异常。每条规则都创建异常、捕获异常，正常校验流程反而依赖异常机制。
2. `ValidateHandler` 被异常协议绑定。处理器必须声明 `throws ValidateException`，链也必须知道捕获哪一种异常；如果以后要返回警告、修正后的值或其他结果，接口会继续膨胀。
3. 这更像“异常聚合循环”，还不是完整的责任链协议。节点没有 `doNext()`，不能传递变换后的对象，也没有让节点主动中断的标准入口；当前代码会无条件尝试执行所有节点。
4. 错误聚合范围仍然只是一个字段。外层 `Validator` 每处理完一个字段就调用一次 `chain.validate`，链重新抛出异常后，后续字段不会继续校验。因此它可以同时收集 `age` 的最大值和最小值错误，却不能收集 `age` 和 `name` 两个字段的全部错误。
5. 重新抛出时只保留 `exception.getMessage()`，原始异常的堆栈、原因和具体处理器信息都被丢弃了。以后如果需要定位“哪个字段的哪个规则失败”，单纯拼接字符串也不够用。

所以阶段四适合用来说明“如何让同一字段上的多个规则都执行”，但不适合作为最终设计。下一步需要把预期的校验结果从异常控制流中分离出来：处理器把错误写入上下文，链负责调度，最后再由上下文统一决定是否抛出异常。同时，上下文也为后面的 `stopChain()`、`doNext()`、当前值和索引提供了统一的位置。

## 阶段五：用 ValidatorContext 传递错误

先定义上下文：

### `src/main/java/chain/validator/ValidatorContext.java`

```java
package chain.validator;

import chain.exception.ValidateException;

import java.util.ArrayList;
import java.util.List;

public class ValidatorContext {
    private final List<String> errorMessages = new ArrayList<>();

    public void appendErrorMessage(String errorMessage) {
        errorMessages.add(errorMessage);
    }

    public void throwExceptionIfNecessary() throws ValidateException {
        if (!errorMessages.isEmpty()) {
            throw new ValidateException(String.join(";", errorMessages));
        }
    }
}
```

接口把上下文传给每个处理器：

### `src/main/java/chain/validator/ValidateHandler.java`

```java
package chain.validator;

public interface ValidateHandler {
    void validate(Object value, ValidatorContext context);
}
```

三个处理器都只记录错误，不再直接抛出校验异常：

### `src/main/java/chain/validator/MaxValidateHandler.java`

```java
package chain.validator;

public class MaxValidateHandler implements ValidateHandler {
    private final int max;

    public MaxValidateHandler(int max) {
        this.max = max;
    }

    @Override
    public void validate(Object value, ValidatorContext context) {
        if (value instanceof Integer intValue && intValue > max) {
            context.appendErrorMessage("值为" + intValue + "不能大于" + max);
        }
    }
}
```

### `src/main/java/chain/validator/MinValidateHandler.java`

```java
package chain.validator;

public class MinValidateHandler implements ValidateHandler {
    private final int min;

    public MinValidateHandler(int min) {
        this.min = min;
    }

    @Override
    public void validate(Object value, ValidatorContext context) {
        if (value instanceof Integer intValue && intValue < min) {
            context.appendErrorMessage("值为" + intValue + "不能小于" + min);
        }
    }
}
```

### `src/main/java/chain/validator/LengthValidateHandler.java`

```java
package chain.validator;

public class LengthValidateHandler implements ValidateHandler {
    private final int length;

    public LengthValidateHandler(int length) {
        this.length = length;
    }

    @Override
    public void validate(Object value, ValidatorContext context) {
        if (value instanceof String stringValue && stringValue.length() > length) {
            context.appendErrorMessage(
                    "长度为" + stringValue.length() + "不能大于" + length);
        }
    }
}
```

链负责创建上下文、调用处理器、统一抛出：

### `src/main/java/chain/validator/ValidatorHandlerChain.java`

```java
package chain.validator;

import java.util.ArrayList;
import java.util.List;

public class ValidatorHandlerChain {
    private final List<ValidateHandler> handlers = new ArrayList<>();

    public void addLastHandler(ValidateHandler handler) {
        handlers.add(handler);
    }

    public void validate(Object value) {
        ValidatorContext context = new ValidatorContext();
        for (ValidateHandler handler : handlers) {
            handler.validate(value, context);
        }
        context.throwExceptionIfNecessary();
    }
}
```

### `src/main/java/chain/validator/Validator.java`

```java
package chain.validator;

import chain.annotation.Length;
import chain.annotation.Max;
import chain.annotation.Min;
import chain.exception.ValidateException;

import java.lang.reflect.Field;

public class Validator {

    public void validate(Object bean) throws ValidateException, IllegalAccessException {
        for (Field field : bean.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            ValidatorHandlerChain chain = buildHandlerChain(field);
            chain.validate(field.get(bean));
        }
    }

    private ValidatorHandlerChain buildHandlerChain(Field field) {
        ValidatorHandlerChain chain = new ValidatorHandlerChain();

        Max max = field.getAnnotation(Max.class);
        if (max != null) {
            chain.addLastHandler(new MaxValidateHandler(max.value()));
        }

        Min min = field.getAnnotation(Min.class);
        if (min != null) {
            chain.addLastHandler(new MinValidateHandler(min.value()));
        }

        Length length = field.getAnnotation(Length.class);
        if (length != null) {
            chain.addLastHandler(new LengthValidateHandler(length.value()));
        }

        return chain;
    }
}
```

这个版本的上下文是字段级的：每个字段重新创建一个 `ValidatorContext`，所以第一个失败字段的链抛出异常后，外层对象校验也会停止。

## 阶段六：给上下文增加 stopChain()

如果某些规则属于“失败即停止”，可以把停止信号放入上下文。

### `src/main/java/chain/validator/ValidatorContext.java`

```java
package chain.validator;

import chain.exception.ValidateException;

import java.util.ArrayList;
import java.util.List;

public class ValidatorContext {
    private final List<String> errorMessages = new ArrayList<>();
    private boolean shouldStop;

    public void appendErrorMessage(String errorMessage) {
        errorMessages.add(errorMessage);
    }

    public void throwExceptionIfNecessary() throws ValidateException {
        if (!errorMessages.isEmpty()) {
            throw new ValidateException(String.join(";", errorMessages));
        }
    }

    public boolean shouldStop() {
        return shouldStop;
    }

    public void stopChain() {
        shouldStop = true;
    }
}
```

### `src/main/java/chain/validator/MaxValidateHandler.java`

```java
package chain.validator;

public class MaxValidateHandler implements ValidateHandler {
    private final int max;

    public MaxValidateHandler(int max) {
        this.max = max;
    }

    @Override
    public void validate(Object value, ValidatorContext context) {
        if (value instanceof Integer intValue && intValue > max) {
            context.appendErrorMessage("值为" + intValue + "不能大于" + max);
            context.stopChain();
        }
    }
}
```

### `src/main/java/chain/validator/ValidatorHandlerChain.java`

```java
package chain.validator;

import java.util.ArrayList;
import java.util.List;

public class ValidatorHandlerChain {
    private final List<ValidateHandler> handlers = new ArrayList<>();

    public void addLastHandler(ValidateHandler handler) {
        handlers.add(handler);
    }

    public void validate(Object value) {
        ValidatorContext context = new ValidatorContext();

        for (ValidateHandler handler : handlers) {
            handler.validate(value, context);
            if (context.shouldStop()) {
                break;
            }
        }

        context.throwExceptionIfNecessary();
    }
}
```

这时 `Max` 失败会记录错误并中断链，`Min` 不会执行。停止信号只有在链循环显式检查 `shouldStop()` 时才有效。

## 阶段七：用 doNext() 明确“是否继续”和“传什么值”

现在把“继续到下一个节点”从链的 `for` 循环中移到处理器协议中。上下文同时保存当前索引和当前值。

### `src/main/java/chain/validator/ValidatorContext.java`

```java
package chain.validator;

import chain.exception.ValidateException;

import java.util.ArrayList;
import java.util.List;

public class ValidatorContext {
    private final List<String> errorMessages = new ArrayList<>();
    private boolean shouldStop;
    private int index;
    private Object value;

    public ValidatorContext(Object value) {
        this.value = value;
    }

    public void appendErrorMessage(String errorMessage) {
        errorMessages.add(errorMessage);
    }

    public void throwExceptionIfNecessary() throws ValidateException {
        if (!errorMessages.isEmpty()) {
            throw new ValidateException(String.join(";", errorMessages));
        }
    }

    public boolean shouldStop() {
        return shouldStop;
    }

    public void stopChain() {
        shouldStop = true;
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
```

### `src/main/java/chain/validator/ValidatorHandlerChain.java`

```java
package chain.validator;

import java.util.ArrayList;
import java.util.List;

public class ValidatorHandlerChain {
    private final List<ValidateHandler> handlers = new ArrayList<>();

    public void addLastHandler(ValidateHandler handler) {
        handlers.add(handler);
    }

    public void validate(Object value) {
        ValidatorContext context = new ValidatorContext(value);

        while (true) {
            int index = context.getCurrentIndex();
            if (index == handlers.size()) {
                break;
            }

            ValidateHandler handler = handlers.get(index);
            handler.validate(context.getValue(), context);

            // 当前节点没有调用 doNext()，索引没有变化，链停止
            if (index == context.getCurrentIndex()) {
                break;
            }
        }

        context.throwExceptionIfNecessary();
    }
}
```

### `src/main/java/chain/validator/ValidateHandler.java`

```java
package chain.validator;

public interface ValidateHandler {
    void validate(Object value, ValidatorContext context);
}
```

### `src/main/java/chain/validator/MaxValidateHandler.java`

```java
package chain.validator;

public class MaxValidateHandler implements ValidateHandler {
    private final int max;

    public MaxValidateHandler(int max) {
        this.max = max;
    }

    @Override
    public void validate(Object value, ValidatorContext context) {
        if (value instanceof Integer intValue && intValue > max) {
            context.appendErrorMessage("值为" + intValue + "不能大于" + max);
            // 是否停止链：实验时取消注释
            // context.stopChain();

            // 每次只打开一行，观察下一个节点收到的值
            // context.doNext(value);
            // context.doNext(19);
            // context.doNext(30);
        }
    }
}
```

### `src/main/java/chain/validator/MinValidateHandler.java`

```java
package chain.validator;

public class MinValidateHandler implements ValidateHandler {
    private final int min;

    public MinValidateHandler(int min) {
        this.min = min;
    }

    @Override
    public void validate(Object value, ValidatorContext context) {
        if (value instanceof Integer intValue && intValue < min) {
            context.appendErrorMessage("值为" + intValue + "不能小于" + min);
            context.doNext(value);
        }
    }
}
```

### `src/main/java/chain/validator/LengthValidateHandler.java`

```java
package chain.validator;

public class LengthValidateHandler implements ValidateHandler {
    private final int length;

    public LengthValidateHandler(int length) {
        this.length = length;
    }

    @Override
    public void validate(Object value, ValidatorContext context) {
        if (value instanceof String stringValue && stringValue.length() > length) {
            context.appendErrorMessage(
                    "长度为" + stringValue.length() + "不能大于" + length);
            context.doNext(value);
        }
    }
}
```

### `src/main/java/chain/validator/Validator.java`

```java
package chain.validator;

import chain.annotation.Length;
import chain.annotation.Max;
import chain.annotation.Min;
import chain.exception.ValidateException;

import java.lang.reflect.Field;

public class Validator {

    public void validate(Object bean) throws ValidateException, IllegalAccessException {
        for (Field field : bean.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            ValidatorHandlerChain chain = buildHandlerChain(field);
            chain.validate(field.get(bean));
        }
    }

    private ValidatorHandlerChain buildHandlerChain(Field field) {
        ValidatorHandlerChain chain = new ValidatorHandlerChain();

        Max max = field.getAnnotation(Max.class);
        if (max != null) {
            chain.addLastHandler(new MaxValidateHandler(max.value()));
        }

        Min min = field.getAnnotation(Min.class);
        if (min != null) {
            chain.addLastHandler(new MinValidateHandler(min.value()));
        }

        Length length = field.getAnnotation(Length.class);
        if (length != null) {
            chain.addLastHandler(new LengthValidateHandler(length.value()));
        }

        return chain;
    }
}
```

### `src/main/java/chain/dto/User.java`

```java
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
```

### `src/main/java/chain/Main.java`

```java
package chain;

import chain.dto.User;
import chain.validator.Validator;

public class Main {
    public static void main(String[] args) throws IllegalAccessException {
        User user = new User(18, "qiaolezi");
        new Validator().validate(user);
    }
}
```

### 当前版本中必须注意的行为

第一，`doNext()` 是当前链真正的推进开关。现在 `MinValidateHandler` 和 `LengthValidateHandler` 只在校验失败时调用 `doNext(value)`；校验成功时不推进，所以“校验通过”并不自动意味着“继续下一个节点”。如果业务要求所有规则都继续执行，应把 `doNext(value)` 放到处理完成后的统一位置。

第二，`stopChain()` 的方法仍然存在，但当前链循环只比较索引是否变化，没有再次检查 `context.shouldStop()`。因此：

- 不调用 `doNext()` 时，链会因为索引不变而停止；
- 调用 `doNext()` 时，即使同时调用了 `stopChain()`，当前实现仍会继续；
- 如果希望停止信号优先，需要在处理器调用之后补回 `if (context.shouldStop()) { break; }`。

## 实验：把运行结果贴在表格中

实验只修改 `MaxValidateHandler` 失败分支，每次只打开一个 `doNext` 调用。当前 `User` 的 `age` 是 18，最大值是 10，最小值是 30，因此是否进入 `MinValidateHandler` 可以从异常消息直接看出来。

| 编号 | `stopChain()` | `doNext(...)` | 传递的值 | 观察重点 | 实际运行结果 |
| --- | --- | --- | --- | --- | --- |
| A | 不调用 | 不调用 | 无 | 索引不变，停在最大值节点 | 待粘贴 |
| B | 调用 | 不调用 | 无 | 当前实现仍因索引不变停止 | 待粘贴 |
| C | 不调用 | 调用 | 原值 `18` | 最小值节点比较 18 | 待粘贴 |
| D | 调用 | 调用 | 原值 `18` | 当前实现仍会进入最小值节点 | 待粘贴 |
| E | 不调用 | 调用 | 新值 `19` | 最小值错误中的比较值变为 19 | 待粘贴 |
| F | 不调用 | 调用 | 新值 `30` | 最小值节点比较 30，不再产生最小值错误 | 待粘贴 |

每组实验运行：

```bash
mvn -q -DskipTests package
java -cp target/classes chain.Main 2>&1
```

项目要求 JDK 21。建议把每组实验的修改代码、完整控制台输出和结论一起贴在表格的最后一列，不要只记录最终异常文本。

## 当前版本的完整源码清单

当前版本的完整源码就是上面阶段七列出的 10 个文件，另外三个注解和异常类保持阶段一的完整实现：

- `src/main/java/chain/annotation/Length.java`
- `src/main/java/chain/annotation/Max.java`
- `src/main/java/chain/annotation/Min.java`
- `src/main/java/chain/exception/ValidateException.java`

这样，从第一阶段到最后阶段的每个新增或变更文件都有完整代码，运行实验时只需要替换对应文件即可。

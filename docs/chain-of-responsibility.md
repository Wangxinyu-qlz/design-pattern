# 从字段校验到责任链：代码如何一步步演进

本文用一个基于注解的 `User` 校验器，完整演示责任链是怎样从固定流程演进出来的，阐述每次代码变更解决了什么问题：规则如何拆开、节点如何排列、错误如何收集，以及节点如何决定继续还是停止。

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
│   ├── Min.java
│   └── Order.java
├── dto/User.java
├── exception/ValidateException.java
├── pipeline/
│   ├── ApplyDiscountStage.java
│   ├── CalculateSubtotalStage.java
│   ├── CalculateTaxStage.java
│   ├── FinalizeOrderStage.java
│   ├── OrderProcess.java
│   ├── PipelineContext.java
│   ├── PipelineMain.java
│   ├── PipelineResult.java
│   ├── PipelineStage.java
│   └── ProcessingPipeline.java
└── validator/
    ├── LengthValidateHandler.java
    ├── MaxValidateHandler.java
    ├── MinValidateHandler.java
    ├── ValidateHandler.java
    ├── Validator.java
    ├── ValidatorHandlerChain.java
    ├── ChainExecutionContext.java
    └── ValidationContext.java
```

下面每个代码块都对应一个完整文件。为了避免重复，阶段中没有变化的文件沿用前一个阶段的完整文件；文章末尾还给出当前版本的全部源码。

## 阶段一：把所有规则写在 Validator 里

先不引入责任链，只完成最小的反射校验闭环。

   `src/main/java/chain/annotation/Length.java`

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

   `src/main/java/chain/annotation/Max.java`

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

   `src/main/java/chain/annotation/Min.java`

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

   `src/main/java/chain/exception/ValidateException.java`

```java
package chain.exception;

public class ValidateException extends RuntimeException {
    public ValidateException(String message) {
        super(message);
    }
}
```

   `src/main/java/chain/dto/User.java`

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

   `src/main/java/chain/validator/Validator.java`

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

   `src/main/java/chain/Main.java`

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

   `src/main/java/chain/validator/ValidateHandler.java`

```java
package chain.validator;

import chain.exception.ValidateException;

public interface ValidateHandler {
    void validate(Object value) throws ValidateException;
}
```

   `src/main/java/chain/validator/MaxValidateHandler.java`

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

   `src/main/java/chain/validator/MinValidateHandler.java`

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

   `src/main/java/chain/validator/LengthValidateHandler.java`

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

   `src/main/java/chain/validator/Validator.java`

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

   `src/main/java/chain/validator/ValidatorHandlerChain.java`

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
用数组还是链表都可以。每个处理器内部保存 next，那就是“链表式责任链”，甚至不需要 List，这种方式适合节点之间直接传递控制权。

```java
interface Handler {
    void setNext(Handler next);

    void handle(Object value);
}
```

`Validator` 只负责组装当前字段的链：

   `src/main/java/chain/validator/Validator.java`

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

   `src/main/java/chain/dto/User.java`

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

   `src/main/java/chain/Main.java`

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

此时链已经存在，但处理器抛出的第一个异常仍会跳出 `for` 循环。如果需要收集所有异常，怎么做？

## 阶段四：在链里收集多个异常

只改链对象，处理器和接口暂时保持不变。

   `src/main/java/chain/validator/ValidatorHandlerChain.java`

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

这样 `age = 18` 会同时得到最大值和最小值错误。

但这只是一个能工作的过渡版本，因为它把“校验失败”当成了异常控制流：

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

## 阶段五：用 ValidatorContext 传递错误

先定义上下文：

   `src/main/java/chain/validator/ValidatorContext.java`

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

   `src/main/java/chain/validator/ValidateHandler.java`

```java
package chain.validator;

public interface ValidateHandler {
    void validate(Object value, ValidatorContext context);
}
```

三个处理器都只记录错误，不再直接抛出校验异常：

   `src/main/java/chain/validator/MaxValidateHandler.java`

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

   `src/main/java/chain/validator/MinValidateHandler.java`

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

   `src/main/java/chain/validator/LengthValidateHandler.java`

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

   `src/main/java/chain/validator/ValidatorHandlerChain.java`

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

   `src/main/java/chain/validator/Validator.java`

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

这个版本的上下文是字段级的：每个字段重新创建一个 `ValidatorContext`，所以第一个失败字段的链抛出异常后，外层对象校验也会停止，如何收集所有字段的异常呢？后文揭晓。

## 阶段六：给上下文增加 stopChain()

如果某些规则属于“失败即停止”，可以把停止信号放入上下文。

   `src/main/java/chain/validator/ValidatorContext.java`

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

   `src/main/java/chain/validator/MaxValidateHandler.java`

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
            // 是否停止链
            context.stopChain();
        }
    }
}
```

   `src/main/java/chain/validator/ValidatorHandlerChain.java`

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

   `src/main/java/chain/validator/ValidatorContext.java`

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

   `src/main/java/chain/validator/ValidatorHandlerChain.java`

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

   `src/main/java/chain/validator/ValidateHandler.java`

```java
package chain.validator;

public interface ValidateHandler {
    void validate(Object value, ValidatorContext context);
}
```

   `src/main/java/chain/validator/MaxValidateHandler.java`

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
            // 控制下一个节点处理的对象，
            // context.doNext(value);
            // context.doNext(19);
            // context.doNext(30);
        }
    }
}
```

   `src/main/java/chain/validator/MinValidateHandler.java`

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

   `src/main/java/chain/validator/LengthValidateHandler.java`

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

   `src/main/java/chain/validator/Validator.java`

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

   `src/main/java/chain/dto/User.java`

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

   `src/main/java/chain/Main.java`

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

### doNext 和 Servlet Filter 的 doFilter

`doNext()` 的思想与 Servlet Filter 的 `doFilter()` 是一致的：当前节点处理完请求后，只有显式调用“链对象的继续方法”，后续节点才会被执行；不调用就停在当前节点。

先看 Jakarta Servlet 的接口契约。过滤器本身接收一个 `FilterChain`，而不是直接拿到下一个过滤器：

```java
public interface Filter {
    void doFilter(ServletRequest request,
                  ServletResponse response,
                  FilterChain chain)
            throws IOException, ServletException;
}
```

`FilterChain` 的继续方法是：

```java
public interface FilterChain {
    void doFilter(ServletRequest request,
                  ServletResponse response)
            throws IOException, ServletException;
}
```

一个典型过滤器会这样写：

```java
public final class AuthenticationFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request,
                         ServletResponse response,
                         FilterChain chain)
            throws IOException, ServletException {
        if (!isAuthenticated(request)) {
            response.getWriter().write("unauthorized");
            return;
        }

        // 继续执行下一个过滤器；不调用就阻断请求
        chain.doFilter(request, response);
    }

    private boolean isAuthenticated(ServletRequest request) {
        return request.getAttribute("user") != null;
    }
}
```

过滤器还可以在继续前后分别处理逻辑：

```java
public final class TimingFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request,
                         ServletResponse response,
                         FilterChain chain)
            throws IOException, ServletException {
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long elapsed = System.nanoTime() - start;
            System.out.println("elapsed=" + elapsed);
        }
    }
}
```

真正推进过滤器位置的是容器实现。以 Tomcat 10.1 的 `ApplicationFilterChain` 为例，下面是 `internalDoFilter` 的源码；安全权限和异常包装代码也保留在示例中，便于看清容器真正如何把当前过滤器和链对象连接起来：

```java
private void internalDoFilter(ServletRequest request, ServletResponse response)
        throws IOException, ServletException {

    // Call the next filter if there is one
    if (pos < n) {
        ApplicationFilterConfig filterConfig = filters[pos++];
        try {
            Filter filter = filterConfig.getFilter();

            if (request.isAsyncSupported()
                    && !filterConfig.getFilterDef().getAsyncSupportedBoolean()) {
                request.setAttribute(Globals.ASYNC_SUPPORTED_ATTR, Boolean.FALSE);
            }

            if (Globals.IS_SECURITY_ENABLED) {
                final ServletRequest req = request;
                final ServletResponse res = response;
                Principal principal = ((HttpServletRequest) req).getUserPrincipal();
                Object[] args = new Object[] { req, res, this };
                SecurityUtil.doAsPrivilege("doFilter", filter, classType,
                        args, principal);
            } else {
                filter.doFilter(request, response, this);
            }
        } catch (IOException | ServletException | RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            t = ExceptionUtils.unwrapInvocationTargetException(t);
            ExceptionUtils.handleThrowable(t);
            throw new ServletException(sm.getString("filterChain.filter"), t);
        }
        return;
    }

    // We fell off the end of the chain -- call the servlet instance
    try {
        if (dispatcherWrapsSameObject) {
            lastServicedRequest.set(request);
            lastServicedResponse.set(response);
        }

        if (request.isAsyncSupported() && !servletSupportsAsync) {
            request.setAttribute(Globals.ASYNC_SUPPORTED_ATTR, Boolean.FALSE);
        }

        // Use potentially wrapped request from this point
        if ((request instanceof HttpServletRequest)
                && (response instanceof HttpServletResponse)
                && Globals.IS_SECURITY_ENABLED) {
            final ServletRequest req = request;
            final ServletResponse res = response;
            Principal principal = ((HttpServletRequest) req).getUserPrincipal();
            Object[] args = new Object[] { req, res };
            SecurityUtil.doAsPrivilege("service", servlet,
                    classTypeUsedInService, args, principal);
        } else {
            servlet.service(request, response);
        }
    } catch (IOException | ServletException | RuntimeException e) {
        throw e;
    } catch (Throwable t) {
        t = ExceptionUtils.unwrapInvocationTargetException(t);
        ExceptionUtils.handleThrowable(t);
        throw new ServletException(sm.getString("filterChain.servlet"), t);
    } finally {
        if (dispatcherWrapsSameObject) {
            lastServicedRequest.set(null);
            lastServicedResponse.set(null);
        }
    }
}
```

源码来源：

- [Jakarta Servlet `Filter.java`](https://github.com/jakartaee/servlet/blob/main/api/src/main/java/jakarta/servlet/Filter.java)
- [Tomcat 10.1 `ApplicationFilterChain.java`](https://github.com/apache/tomcat/blob/10.1.x/java/org/apache/catalina/core/ApplicationFilterChain.java)

这个实现有三个关键点：

1. `pos++` 就是链的游标推进。过滤器拿到的 `this` 仍然是同一个 `FilterChain` 对象，但下一次调用时位置已经变化。
2. `filter.doFilter(request, response, this)` 把链对象传给当前过滤器。当前过滤器调用 `chain.doFilter(...)`，才会回到容器并执行下一个过滤器。
3. 当 `pos == n` 时，过滤器链结束，容器调用最终的 `servlet.service(...)`。

### 和当前 Validator 链的对应关系

| Servlet Filter | 当前 Validator 链 |
| --- | --- |
| `ServletRequest` / `ServletResponse` | `ValidatorContext.value` |
| 当前 `Filter` | 当前 `ValidateHandler` |
| `FilterChain chain` | `ValidatorContext` 中的索引和 `ValidatorHandlerChain` |
| `chain.doFilter(request, response)` | `context.doNext(value)` |
| 不调用 `chain.doFilter` | 不调用 `doNext()` |
| `pos++` | `index++` |
| 最后调用 `servlet.service` | `index == handlers.size()`，链结束后检查错误 |

两者的核心控制关系可以写成：

```text
Filter:
    当前过滤器处理 request/response
    -> chain.doFilter(...) 才继续
    -> 不调用则阻断

Validator:
    当前处理器处理 value/context
    -> context.doNext(nextValue) 才继续
    -> 不调用则停止
```

但两者不是完全相同的实现。Servlet Filter 通常通过递归调用 `chain.doFilter` 形成“调用前处理、调用后处理”的嵌套结构；当前 Validator 是一个 `while` 循环，每个节点通过修改上下文索引来驱动下一轮循环。前者把继续动作暴露为链对象的方法，后者把继续动作暴露为上下文的方法。

另外，Filter 可以传递包装后的 request/response，这对应当前的：

```java
context.doNext(newValue);
```

因此 `doNext(19)` 不只是“跳到下一个节点”，还表示“当前节点处理完->**后处理**->下一个节点处理”。

## 你发现"bug"了吗？

在阶段七刚引入 `doNext()` 时，`MinValidateHandler` 和 `LengthValidateHandler` 只在校验失败时推进。那种写法会导致“校验成功即停”。

如何解决？

现在三个验证处理器都遵守同一个规则：只要输入类型匹配，当前节点无论校验成功还是失败，最后都调用一次 `context.doNext(value)`。

   `src/main/java/chain/validator/MaxValidateHandler.java`

```java
package chain.validator;

import chain.exception.ValidateException;

public class MaxValidateHandler implements ValidateHandler {
    private final int max;

    public MaxValidateHandler(int max) {
        this.max = max;
    }

    @Override
    public void validate(Object value, ValidatorContext context) throws ValidateException {
        if (value instanceof Integer intValue) {
            if (intValue > max) {
                context.appendErrorMessage("值为" + intValue + "不能大于" + max);
            }

            // 无论是否校验通过，最后都调用一次 doNext
            context.doNext(value);
        }
    }
}
```
`MinValidateHandler` 和 `LengthValidateHandler` 也遵守这个规则。

### 这次调整改变了什么

以当前 `User(18, "qiaolezi")` 为例，年龄链的执行顺序变成：

```text
Max(10)：18 超过 10，记录错误，doNext(18)
    -> Min(30)：18 小于 30，记录错误，doNext(18)
        -> index 到达 handlers.size()，链结束
        -> 统一抛出两个错误
```

之前 `Max` 失败后没有推进，`Min` 不会执行；现在失败只代表“记录错误”，不再代表“停止链”。

## 如何收集所有字段的校验异常？

即使字段内部的节点都能继续，`Validator` 仍然会在每条字段链结束时抛出异常：

```text
Validator 处理第一个字段
    -> ValidatorHandlerChain 执行字段链
    -> 字段链立刻抛出 ValidateException
    -> Validator 的字段循环被打断
    -> 后续字段没有机会执行
```

### 方案一

把两个层次分开：字段链只负责执行并返回结果，对象级 `Validator` 负责汇总所有字段的错误，最后统一抛出一次异常。

每个字段仍然使用独立的 `ValidatorContext`，这样 `index` 和 `value` 不会在字段之间串行污染；对象级只合并错误列表。

   `src/main/java/chain/validator/ValidatorContext.java`

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

    public List<String> getErrorMessages() {
        return List.copyOf(errorMessages);
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

   `src/main/java/chain/validator/ValidatorHandlerChain.java`

```java
package chain.validator;

import java.util.ArrayList;
import java.util.List;

public class ValidatorHandlerChain {
    private final List<ValidateHandler> handlers = new ArrayList<>();

    public void addLastHandler(ValidateHandler handler) {
        handlers.add(handler);
    }

    public ValidatorContext validate(Object value) {
        ValidatorContext context = new ValidatorContext(value);

        while (true) {
            int index = context.getCurrentIndex();
            if (index == handlers.size()) {
                break;
            }

            ValidateHandler handler = handlers.get(index);
            handler.validate(context.getValue(), context);

            // 当前处理器没有调用 doNext()，字段链停止
            if (index == context.getCurrentIndex()) {
                break;
            }
        }

        return context;
    }
}
```

   `src/main/java/chain/validator/Validator.java`

```java
package chain.validator;

import chain.annotation.Length;
import chain.annotation.Max;
import chain.annotation.Min;
import chain.exception.ValidateException;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class Validator {

    public void validate(Object bean) throws ValidateException, IllegalAccessException {
        List<String> errorMessages = new ArrayList<>();

        for (Field field : bean.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            ValidatorHandlerChain chain = buildHandlerChain(field);
            ValidatorContext context = chain.validate(field.get(bean));
            errorMessages.addAll(context.getErrorMessages());
        }

        if (!errorMessages.isEmpty()) {
            throw new ValidateException(String.join(";", errorMessages));
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

优化后的执行结果会从“只看到第一个字段的错误”变为收集所有字段的错误：

```text
值为18不能大于10;值为18不能小于30;长度为8不能大于4
```

### 方案二：总上下文与字段链执行上下文分离

上一节的做法已经能聚合所有字段错误，但 `Validator` 需要从字段上下文中取出错误列表，再自己组织最终异常。更完整的设计是建立一个对象级总上下文：

```text
ValidationContext                 对象级，总错误收集和最终抛错
    └── ChainExecutionContext     字段级，index/value/shouldStop/doNext
            └── 当前字段的 ValidateHandler 链
```

这样可以让 `throwExceptionIfNecessary()` 回到唯一、明确的异常边界：

```text
Validator 创建一个 ValidationContext
    -> 遍历所有字段
        -> 为当前字段创建 ChainExecutionContext
        -> 执行当前字段的处理器链
        -> 错误写入同一个 ValidationContext
    -> 所有字段结束
    -> ValidationContext.throwExceptionIfNecessary()
```

不能直接把原来的执行上下文在所有字段之间复用，因为它同时保存了三类不同生命周期的状态：

| 状态 | 生命周期 | 是否应该跨字段共享 |
| --- | --- | --- |
| 错误列表 | 整个对象校验 | 是 |
| `index` | 当前字段链 | 否 |
| `value` | 当前字段链 | 否 |
| `shouldStop` | 当前字段链 | 否 |

    `src/main/java/chain/validator/ValidationContext.java`

```java
package chain.validator;

import chain.exception.ValidateException;

import java.util.ArrayList;
import java.util.List;

/**
 * 保存整个对象校验过程中的结果。
 */
public class ValidationContext {
    private final List<String> errorMessages = new ArrayList<>();

    public void appendErrorMessage(String errorMessage) {
        errorMessages.add(errorMessage);
    }

    public void throwExceptionIfNecessary() throws ValidateException {
        if (errorMessages.isEmpty()) {
            return;
        }
        throw new ValidateException(String.join(";", errorMessages));
    }
}
```

    `src/main/java/chain/validator/ChainExecutionContext.java`

```java
package chain.validator;

/**
 * 保存一条字段责任链的执行状态。
 */
public class ChainExecutionContext {
    private final ValidationContext validationContext;
    private boolean shouldStop;
    private int index;
    private Object value;

    public ChainExecutionContext(
            Object value,
            ValidationContext validationContext
    ) {
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

    `src/main/java/chain/validator/ValidateHandler.java`

```java
package chain.validator;

public interface ValidateHandler {
    void validate(Object value, ChainExecutionContext context);
}
```

处理器只依赖字段链执行上下文。它可以记录错误，但不直接持有对象级上下文，也不负责最终抛异常。

    `src/main/java/chain/validator/MaxValidateHandler.java`

```java
package chain.validator;

import chain.exception.ValidateException;

public class MaxValidateHandler implements ValidateHandler {
    private final int max;

    public MaxValidateHandler(int max) {
        this.max = max;
    }

    @Override
    public void validate(Object value, ChainExecutionContext context)
            throws ValidateException {
        if (value instanceof Integer intValue) {
            if (intValue > max) {
                context.appendErrorMessage("值为" + intValue + "不能大于" + max);
            }
            context.doNext(value);
        }
    }
}
```

    `src/main/java/chain/validator/MinValidateHandler.java`

```java
package chain.validator;

import chain.exception.ValidateException;

public class MinValidateHandler implements ValidateHandler {
    private final int min;

    public MinValidateHandler(int min) {
        this.min = min;
    }

    @Override
    public void validate(Object value, ChainExecutionContext context)
            throws ValidateException {
        if (value instanceof Integer intValue) {
            if (intValue < min) {
                context.appendErrorMessage("值为" + intValue + "不能小于" + min);
            }
            context.doNext(value);
        }
    }
}
```

    `src/main/java/chain/validator/LengthValidateHandler.java`

```java
package chain.validator;

import chain.exception.ValidateException;

public class LengthValidateHandler implements ValidateHandler {
    private final int length;

    public LengthValidateHandler(int length) {
        this.length = length;
    }

    @Override
    public void validate(Object value, ChainExecutionContext context)
            throws ValidateException {
        if (value instanceof String stringValue) {
            if (stringValue.length() > length) {
                context.appendErrorMessage(
                        "长度为" + stringValue.length() + "不能大于" + length);
            }
            context.doNext(value);
        }
    }
}
```

    `src/main/java/chain/validator/ValidatorHandlerChain.java`

```java
package chain.validator;

import java.util.ArrayList;
import java.util.List;

public class ValidatorHandlerChain {
    private final List<ValidateHandler> handlers = new ArrayList<>();

    public void addLastHandler(ValidateHandler handler) {
        handlers.add(handler);
    }

    public void validate(Object value, ValidationContext validationContext) {
        ChainExecutionContext context =
                new ChainExecutionContext(value, validationContext);

        while (true) {
            int index = context.getCurrentIndex();
            if (index == handlers.size()) {
                break;
            }

            ValidateHandler handler = handlers.get(index);
            handler.validate(context.getValue(), context);

            // 当前处理器没有调用 doNext()，当前字段链停止
            if (index == context.getCurrentIndex()) {
                break;
            }
        }
    }
}
```

    `src/main/java/chain/validator/Validator.java`

```java
package chain.validator;

import chain.annotation.Length;
import chain.annotation.Max;
import chain.annotation.Min;
import chain.exception.ValidateException;

import java.lang.reflect.Field;

public class Validator {

    public void validate(Object bean)
            throws ValidateException, IllegalAccessException {
        ValidationContext validationContext = new ValidationContext();

        for (Field field : bean.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            ValidatorHandlerChain chain = buildHandlerChain(field);
            chain.validate(field.get(bean), validationContext);
        }

        validationContext.throwExceptionIfNecessary();
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

这套结构保留了字段链的 `doNext()` 语义，同时让对象级上下文成为唯一的错误出口。当前 `User(18, "qiaolezi")` 会在所有字段处理完成后统一得到：

```text
值为18不能大于10;值为18不能小于30;长度为8不能大于4
```

需要继续注意：`ValidatorHandlerChain` 当前仍然通过索引是否变化判断停止，尚未把 `shouldStop()` 接入循环；如果后续要求 `stopChain()` 优先于 `doNext()`，应在处理器调用后增加显式的停止判断。

### 方案三：从 stopChain 版本直接建立总上下文

如果责任链还停留在 `stopChain()` 协议，没有引入 `doNext()`、`index` 和可变的 `value`，确实可以采用更简单的实现：让一个 `ValidatorContext` 从对象校验开始一直传到所有字段链，字段链只负责追加错误，`Validator` 在所有字段完成后调用一次 `throwExceptionIfNecessary()`。

#### 总体调用方式

```java
ValidatorContext context = new ValidatorContext();

for (Field field : declaredFields) {
    ValidatorHandlerChain chain = buildHandlerChain(field);
    chain.validate(field.get(bean), context);
}

context.throwExceptionIfNecessary();
```

    `ValidatorContext`

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

    `ValidatorHandlerChain`

```java
package chain.validator;

import java.util.ArrayList;
import java.util.List;

public class ValidatorHandlerChain {
    private final List<ValidateHandler> handlers = new ArrayList<>();

    public void addLastHandler(ValidateHandler handler) {
        handlers.add(handler);
    }

    public void validate(Object value, ValidatorContext context) {
        for (ValidateHandler handler : handlers) {
            handler.validate(value, context);

            if (context.shouldStop()) {
                break;
            }
        }
    }
}
```

    `Validator`

```java
package chain.validator;

import chain.annotation.Length;
import chain.annotation.Max;
import chain.annotation.Min;
import chain.exception.ValidateException;

import java.lang.reflect.Field;

public class Validator {

    public void validate(Object bean)
            throws ValidateException, IllegalAccessException {
        ValidatorContext context = new ValidatorContext();

        for (Field field : bean.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            ValidatorHandlerChain chain = buildHandlerChain(field);
            chain.validate(field.get(bean), context);
        }

        context.throwExceptionIfNecessary();
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

#### 这个方案为什么更简单

它的上下文只有两类状态：

- `errorMessages`：整个对象共享；
- `shouldStop`：由链读取的停止信号。

因为没有 `index` 和 `value`，字段链本身使用 `for` 循环遍历处理器，不需要为每个字段创建独立的执行上下文。因此它可以直接把同一个上下文传给所有字段链，并在对象循环结束后统一抛错。

#### 这个方案的边界

1. 它只适合 `stopChain()` 协议。如果 `Max` 调用了 `stopChain()`，共享上下文里的 `shouldStop` 会一直为 `true`；下一个字段的链一开始就可能停止。因此必须先明确 `stopChain()` 的作用域是“停止当前字段链”还是“停止整个对象校验”。
2. 它不能原样用于当前 `doNext()` 实现。当前上下文还保存 `index` 和 `value`，如果多个字段共享同一个上下文，第二个字段会继承第一个字段的游标和对象值，导致跳过节点或比较错误的对象。
3. 如果希望保留一个总错误上下文，同时支持当前的 `doNext()`，就需要把执行游标和当前值重新拆出去，也就是上一节的 `ValidationContext + ChainExecutionContext` 方案。

## 如何控制链上节点的顺序？

本节的代码演进对应以下独立提交，便于按步骤学习和回看：

| 演进步骤 | 提交 |
| --- | --- |
| 使用 `order` 字段 | `f0034fd` |
| 使用自定义 `chain.annotation.Order` | `ec4af58` |
| 使用 Spring `org.springframework.core.annotation.Order` | `dadc258` |
| 由 Spring 注入有序工厂列表 | `f3d3eca` |

当前链虽然通过 `addLastHandler` 组装节点，但“添加的先后”不应该等同于“业务执行顺序”。当校验规则增多、组装逻辑分散到工厂或配置类后，依赖调用顺序会让执行顺序变得隐蔽。

先进行第一步演进：把顺序放到处理器的 `order` 字段中，链只根据字段排序，不再依赖 `addLastHandler` 的调用顺序。

### 第一步：使用 order 字段

#### 处理器增加 order 字段

```java
public interface ValidateHandler {
    void validate(Object value, ChainExecutionContext context);

    int getOrder();
}
```

以最大值处理器为例：

```java
public class MaxValidateHandler implements ValidateHandler {
    private final int max;
    private final int order;

    public MaxValidateHandler(int max, int order) {
        this.max = max;
        this.order = order;
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    public void validate(Object value, ChainExecutionContext context) {
        if (value instanceof Integer intValue) {
            if (intValue > max) {
                context.appendErrorMessage("值为" + intValue + "不能大于" + max);
            }
            context.doNext(value);
        }
    }
}
```

`MinValidateHandler` 和 `LengthValidateHandler` 使用同样的 `order` 字段。

#### 链根据 order 字段排序

链在添加节点时排序：

```java
public class ValidatorHandlerChain {
    private final List<ValidateHandler> handlers = new ArrayList<>();

    public void addLastHandler(ValidateHandler handler) {
        handlers.add(handler);
        handlers.sort(Comparator.comparingInt(ValidateHandler::getOrder));
    }

    public void validate(Object value, ValidationContext validationContext) {
        ChainExecutionContext context =
                new ChainExecutionContext(value, validationContext);

        while (true) {
            int index = context.getCurrentIndex();
            if (index == handlers.size()) {
                break;
            }

            ValidateHandler handler = handlers.get(index);
            handler.validate(context.getValue(), context);

            if (index == context.getCurrentIndex()) {
                break;
            }
        }
    }
}
```

组装链时显式指定顺序：

```java
chain.addLastHandler(new MaxValidateHandler(max.value(), 10));
chain.addLastHandler(new MinValidateHandler(min.value(), 20));
chain.addLastHandler(new LengthValidateHandler(length.value(), 30));
```

把 `Min` 的顺序改成 `5`，它就会在 `Max` 之前执行；即使组装代码仍然按 `Max -> Min -> Length` 添加，实际执行也会按 `order` 排序后的顺序运行。

#### 这一阶段的优点和问题

- 优点：执行顺序从调用位置移动到了处理器配置中，链的调度逻辑更明确；
- 问题：`order` 仍然需要通过构造方法传入，`Validator` 仍然知道每个处理器的顺序数字；
- 问题：如果多个地方创建同一个处理器，可能传入不同的顺序，顺序规则没有和处理器类型绑定。

因此下一步把顺序从构造参数中抽出来，改用类级别的 `@Order` 注解。

### 第二步：使用自定义 @Order 注解

#### 定义 @Order 注解

```java
package chain.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Order {
    int value();
}
```

#### 把顺序声明在处理器类上

```java
@Order(10)
public class MaxValidateHandler implements ValidateHandler {
    private final int max;

    public MaxValidateHandler(int max) {
        this.max = max;
    }

    @Override
    public void validate(Object value, ChainExecutionContext context) {
        if (value instanceof Integer intValue) {
            if (intValue > max) {
                context.appendErrorMessage("值为" + intValue + "不能大于" + max);
            }
            context.doNext(value);
        }
    }
}
```

`MinValidateHandler` 和 `LengthValidateHandler` 分别声明 `@Order(20)`、`@Order(30)`，构造方法只接收自己的校验参数：

```java
@Order(20)
public class MinValidateHandler implements ValidateHandler {
    private final int min;

    public MinValidateHandler(int min) {
        this.min = min;
    }

    @Override
    public void validate(Object value, ChainExecutionContext context) {
        if (value instanceof Integer intValue) {
            if (intValue < min) {
                context.appendErrorMessage("值为" + intValue + "不能小于" + min);
            }
            context.doNext(value);
        }
    }
}
```

```java
@Order(30)
public class LengthValidateHandler implements ValidateHandler {
    private final int length;

    public LengthValidateHandler(int length) {
        this.length = length;
    }

    @Override
    public void validate(Object value, ChainExecutionContext context) {
        if (value instanceof String stringValue) {
            if (stringValue.length() > length) {
                context.appendErrorMessage(
                        "长度为" + stringValue.length() + "不能大于" + length);
            }
            context.doNext(value);
        }
    }
}
```

#### 链读取注解并排序

```java
public class ValidatorHandlerChain {
    private final List<ValidateHandler> handlers = new ArrayList<>();

    public void addLastHandler(ValidateHandler handler) {
        handlers.add(handler);
        handlers.sort(Comparator.comparingInt(this::getOrder));
    }

    private int getOrder(ValidateHandler handler) {
        Order order = handler.getClass().getAnnotation(Order.class);
        if (order == null) {
            throw new IllegalArgumentException(
                    "Missing @Order on " + handler.getClass().getName());
        }
        return order.value();
    }

    public void validate(Object value, ValidationContext validationContext) {
        ChainExecutionContext context =
                new ChainExecutionContext(value, validationContext);

        while (true) {
            int index = context.getCurrentIndex();
            if (index == handlers.size()) {
                break;
            }

            ValidateHandler handler = handlers.get(index);
            handler.validate(context.getValue(), context);

            if (index == context.getCurrentIndex()) {
                break;
            }
        }
    }
}
```

`Validator` 的组装代码因此变成：

```java
if (max != null) {
    chain.addLastHandler(new MaxValidateHandler(max.value()));
}
if (min != null) {
    chain.addLastHandler(new MinValidateHandler(min.value()));
}
if (length != null) {
    chain.addLastHandler(new LengthValidateHandler(length.value()));
}
```

### 扩展方案：使用 Spring 的 @Order

上面的 `chain.annotation.Order` 是项目自定义注解。Spring 已经提供了同名但不同包的注解：

```java
import org.springframework.core.annotation.Order;
```

Spring 的 `@Order` 使用规则是数值越小优先级越高。它主要用于 Spring 管理的组件排序，例如将多个处理器注入 `List<ValidateHandler>` 时，Spring 会根据 `@Order` 排列集合中的元素。

#### 组件方式

```java
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class MaxValidateHandler implements ValidateHandler {
    @Override
    public void validate(Object value, ChainExecutionContext context) {
        // 最大值校验
        context.doNext(value);
    }
}
```

```java
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
public class MinValidateHandler implements ValidateHandler {
    @Override
    public void validate(Object value, ChainExecutionContext context) {
        // 最小值校验
        context.doNext(value);
    }
}
```

链可以直接接收 Spring 排好序的处理器列表：

```java
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SpringValidatorHandlerChain {
    private final List<ValidateHandler> handlers;

    public SpringValidatorHandlerChain(List<ValidateHandler> handlers) {
        this.handlers = List.copyOf(handlers);
    }

    public void validate(Object value, ValidationContext validationContext) {
        ChainExecutionContext context =
                new ChainExecutionContext(value, validationContext);

        for (ValidateHandler handler : handlers) {
            handler.validate(context.getValue(), context);
            // 具体的 doNext 判断仍由责任链协议负责
        }
    }
}
```

这里的顺序来源不再是链中的 `handlers.sort(...)`，而是 Spring 的集合注入排序：

```text
Spring 扫描 @Component
    -> 读取每个处理器的 @Order
    -> 按 order 排序
    -> 注入 List<ValidateHandler>
    -> 责任链按列表顺序执行
```

#### @Bean 方法方式

如果处理器不是组件类，也可以在配置类的 `@Bean` 方法上声明顺序：

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
public class ValidatorHandlerConfiguration {

    @Bean
    @Order(10)
    public ValidateHandler maxValidateHandler() {
        return new MaxValidateHandler(10);
    }

    @Bean
    @Order(20)
    public ValidateHandler minValidateHandler() {
        return new MinValidateHandler(30);
    }
}
```

#### 和当前项目的差异

当前项目已经升级为 Spring Boot，并实际采用了 Spring 的排序能力。由于处理器携带字段上的具体规则参数，项目没有把 `MaxValidateHandler` 等直接注册为单例组件，而是注册带 `@Order` 的 `ValidateHandlerFactory`：Spring 先对工厂列表排序，`Validator` 再根据字段注解创建本次校验所需的处理器。

另外，当前项目的 `MaxValidateHandler`、`MinValidateHandler` 和 `LengthValidateHandler` 都携带字段上的具体规则参数，例如 `@Max(10)`、`@Min(30)`。如果把它们直接注册成单例 Bean，就无法同时表示不同字段上的不同阈值。实际接入 Spring 时，通常应该注入带 `@Order` 的处理器工厂或规则模板，再由字段链根据注解创建带参数的执行实例：

```java
public interface ValidateHandlerFactory {
    boolean supports(Field field);

    ValidateHandler create(Field field);
}
```

因此，Spring 的 `@Order` 解决的是“容器如何排列工厂 Bean”，责任链则按注入列表顺序执行本次字段校验节点。顺序规则与工厂类型绑定，处理器实例仍然可以携带字段级参数。

## 另一条演进方向：从责任链到流水线

前面的字段校验属于典型的责任链：每个节点处理后，由节点决定是否调用 `doNext()`。节点既负责业务处理，也拥有“继续还是停止”的控制权。

但有些业务不是“找到某个节点处理即可”，而是“让多个节点依次加工同一个对象”。例如一笔订单需要依次完成：

```text
订单草稿
    -> 计算商品小计
    -> 计算折扣
    -> 计算税额
    -> 计算应付金额并完成订单
```

这时更适合把责任链演进成流水线（Pipeline）：

- 每个节点都有明确的加工职责；
- 每个节点接收上一个节点的输出，并返回新的输出；
- 正常情况下所有节点都会执行；
- 节点不调用 `doNext()`，由流水线容器统一推进。

### 为什么由容器推进

责任链中，“是否还需要其他处理者”是节点的业务决策，所以 `doNext()` 放在节点里是合理的。流水线中，“按工序执行全部节点”是容器的结构性保证。如果仍让节点手动调用 `doNext()`，某个节点遗漏调用就会让后续工序静默失效。

因此，这里由 `ProcessingPipeline` 遍历节点，节点只关心两件事：读取当前对象，返回加工后的对象。

### 完整代码

#### PipelineStage.java

```java
package chain.pipeline;

/**
 * 流水线节点：接收上一个节点的输出，并返回下一个节点的输入。
 */
public interface PipelineStage<T> {
    String name();

    T process(T input, PipelineContext context);
}
```

#### PipelineContext.java

```java
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
```

`PipelineContext` 是一次执行级别的总上下文。示例中它只记录轨迹，实际项目还可以放入请求编号、租户信息、开始时间和各节点共享的附加数据。它与被加工的 `OrderProcess` 是两个不同概念：前者描述“本次执行”，后者描述“业务对象当前状态”。

#### PipelineResult.java

```java
package chain.pipeline;

import java.util.List;
import java.util.Objects;

public record PipelineResult<T>(T value, List<String> trace) {
    public PipelineResult {
        Objects.requireNonNull(value, "value");
        trace = List.copyOf(trace);
    }
}
```

#### ProcessingPipeline.java

```java
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
```

这里使用 `ArrayList` 保存节点。流水线的典型用法是“组装一次，按顺序遍历很多次”，数组结构的顺序遍历简单且对缓存更友好。如果业务需要频繁在中间插入或删除节点，可以考虑链表；但运行期频繁改链本身就会带来并发和可预测性问题，通常更适合重新组装一条新流水线。

#### OrderProcess.java

```java
package chain.pipeline;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 在流水线中逐步补全金额信息的不可变订单对象。
 */
public record OrderProcess(
        String orderNo,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal tax,
        BigDecimal payable,
        String status
) {
    public OrderProcess {
        Objects.requireNonNull(orderNo, "orderNo");
        Objects.requireNonNull(unitPrice, "unitPrice");
        Objects.requireNonNull(subtotal, "subtotal");
        Objects.requireNonNull(discount, "discount");
        Objects.requireNonNull(tax, "tax");
        Objects.requireNonNull(payable, "payable");
        Objects.requireNonNull(status, "status");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be greater than zero");
        }
    }

    public static OrderProcess draft(String orderNo, BigDecimal unitPrice, int quantity) {
        BigDecimal zero = money(BigDecimal.ZERO);
        return new OrderProcess(
                orderNo,
                money(unitPrice),
                quantity,
                zero,
                zero,
                zero,
                zero,
                "DRAFT");
    }

    public OrderProcess withSubtotal(BigDecimal value) {
        return new OrderProcess(
                orderNo, unitPrice, quantity, money(value), discount, tax, payable, status);
    }

    public OrderProcess withDiscount(BigDecimal value) {
        return new OrderProcess(
                orderNo, unitPrice, quantity, subtotal, money(value), tax, payable, status);
    }

    public OrderProcess withTax(BigDecimal value) {
        return new OrderProcess(
                orderNo, unitPrice, quantity, subtotal, discount, money(value), payable, status);
    }

    public OrderProcess complete(BigDecimal value) {
        return new OrderProcess(
                orderNo, unitPrice, quantity, subtotal, discount, tax,
                money(value), "READY_TO_PAY");
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
```

让四个节点每次返回一个新的 `OrderProcess`。这样可以从方法签名上看出数据流向，也避免某个节点意外改写与自己无关的字段。

#### CalculateSubtotalStage.java

```java
package chain.pipeline;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class CalculateSubtotalStage implements PipelineStage<OrderProcess> {
    @Override
    public String name() {
        return "calculate-subtotal";
    }

    @Override
    public OrderProcess process(OrderProcess input, PipelineContext context) {
        BigDecimal subtotal = input.unitPrice()
                .multiply(BigDecimal.valueOf(input.quantity()))
                .setScale(2, RoundingMode.HALF_UP);
        context.addTrace(name(), "subtotal=" + subtotal);
        return input.withSubtotal(subtotal);
    }
}
```

#### ApplyDiscountStage.java

```java
package chain.pipeline;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class ApplyDiscountStage implements PipelineStage<OrderProcess> {
    private static final BigDecimal THRESHOLD = new BigDecimal("100.00");
    private static final BigDecimal DISCOUNT_RATE = new BigDecimal("0.10");

    @Override
    public String name() {
        return "apply-discount";
    }

    @Override
    public OrderProcess process(OrderProcess input, PipelineContext context) {
        BigDecimal discount = BigDecimal.ZERO;
        if (input.subtotal().compareTo(THRESHOLD) >= 0) {
            discount = input.subtotal().multiply(DISCOUNT_RATE);
        }
        discount = discount.setScale(2, RoundingMode.HALF_UP);
        context.addTrace(name(), "discount=" + discount);
        return input.withDiscount(discount);
    }
}
```

#### CalculateTaxStage.java

```java
package chain.pipeline;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class CalculateTaxStage implements PipelineStage<OrderProcess> {
    private static final BigDecimal TAX_RATE = new BigDecimal("0.06");

    @Override
    public String name() {
        return "calculate-tax";
    }

    @Override
    public OrderProcess process(OrderProcess input, PipelineContext context) {
        BigDecimal taxableAmount = input.subtotal().subtract(input.discount());
        BigDecimal tax = taxableAmount
                .multiply(TAX_RATE)
                .setScale(2, RoundingMode.HALF_UP);
        context.addTrace(name(), "tax=" + tax);
        return input.withTax(tax);
    }
}
```

#### FinalizeOrderStage.java

```java
package chain.pipeline;

import java.math.BigDecimal;

public class FinalizeOrderStage implements PipelineStage<OrderProcess> {
    @Override
    public String name() {
        return "finalize-order";
    }

    @Override
    public OrderProcess process(OrderProcess input, PipelineContext context) {
        BigDecimal payable = input.subtotal()
                .subtract(input.discount())
                .add(input.tax());
        context.addTrace(name(), "payable=" + payable);
        return input.complete(payable);
    }
}
```

#### PipelineMain.java

```java
package chain.pipeline;

import java.math.BigDecimal;

public class PipelineMain {
    public static void main(String[] args) {
        ProcessingPipeline<OrderProcess> pipeline = new ProcessingPipeline<OrderProcess>()
                .addLast(new CalculateSubtotalStage())
                .addLast(new ApplyDiscountStage())
                .addLast(new CalculateTaxStage())
                .addLast(new FinalizeOrderStage());

        OrderProcess order = OrderProcess.draft(
                "ORDER-001",
                new BigDecimal("68.00"),
                2);

        PipelineResult<OrderProcess> result = pipeline.execute(order);
        result.trace().forEach(System.out::println);

        OrderProcess completed = result.value();
        System.out.println("orderNo=" + completed.orderNo());
        System.out.println("subtotal=" + completed.subtotal());
        System.out.println("discount=" + completed.discount());
        System.out.println("tax=" + completed.tax());
        System.out.println("payable=" + completed.payable());
        System.out.println("status=" + completed.status());
    }
}
```

### 对象如何流过各个节点

初始订单的单价是 `68.00`，数量是 `2`。每个节点看到的是上一个节点的返回值：

| 节点 | 读取的数据 | 新增或变更的数据 |
| --- | --- | --- |
| `CalculateSubtotalStage` | `unitPrice=68.00, quantity=2` | `subtotal=136.00` |
| `ApplyDiscountStage` | `subtotal=136.00` | `discount=13.60` |
| `CalculateTaxStage` | `subtotal=136.00, discount=13.60` | `tax=7.34` |
| `FinalizeOrderStage` | `subtotal, discount, tax` | `payable=129.74, status=READY_TO_PAY` |

### 运行结果

```text
calculate-subtotal: start
calculate-subtotal: subtotal=136.00
calculate-subtotal: completed
apply-discount: start
apply-discount: discount=13.60
apply-discount: completed
calculate-tax: start
calculate-tax: tax=7.34
calculate-tax: completed
finalize-order: start
finalize-order: payable=129.74
finalize-order: completed
orderNo=ORDER-001
subtotal=136.00
discount=13.60
tax=7.34
payable=129.74
status=READY_TO_PAY
```

从轨迹可以看到，没有任何节点调用 `doNext()`，但容器仍然确保四个节点按组装顺序全部执行。

### 责任链与流水线的差异

| 对比项 | 责任链 | 流水线 |
| --- | --- | --- |
| 核心目标 | 把请求交给合适的节点处理 | 让多个节点依次加工数据 |
| 默认执行方式 | 可继续，也可中断 | 正常情况下全部执行 |
| 推进权 | 节点调用 `doNext()` | 容器自动遍历 |
| 节点输出 | 可以只处理或记录结果 | 作为下一节点的输入 |
| 中断语义 | 是正常业务能力 | 通常代表异常或额外的短路策略 |
| 典型场景 | 校验、请求拦截、权限判定 | 数据清洗、订单计算、文档后处理 |

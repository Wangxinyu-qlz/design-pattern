package chain.web;

import chain.dto.User;
import chain.pipeline.OrderProcess;
import chain.pipeline.PipelineResult;
import chain.pipeline.ProcessingPipeline;
import chain.validator.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ChainController {
    private final Validator validator = new Validator();
    private final ProcessingPipeline<OrderProcess> orderPipeline;

    public ChainController(ProcessingPipeline<OrderProcess> orderPipeline) {
        this.orderPipeline = orderPipeline;
    }

    @PostMapping("/validate")
    public Map<String, Object> validate(@RequestBody User user) throws IllegalAccessException {
        validator.validate(user);
        return Map.of("valid", true);
    }

    @PostMapping("/orders/preview")
    public PipelineResult<OrderProcess> preview(@RequestBody OrderRequest request) {
        return orderPipeline.execute(OrderProcess.draft(request.orderNo(), request.unitPrice(), request.quantity()));
    }

    @ExceptionHandler({IllegalArgumentException.class, chain.exception.ValidateException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badRequest(RuntimeException exception) {
        return Map.of("error", exception.getMessage());
    }

    public record OrderRequest(String orderNo, BigDecimal unitPrice, int quantity) {
    }
}

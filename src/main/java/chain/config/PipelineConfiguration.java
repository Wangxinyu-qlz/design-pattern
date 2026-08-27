package chain.config;

import chain.pipeline.ApplyDiscountStage;
import chain.pipeline.CalculateSubtotalStage;
import chain.pipeline.CalculateTaxStage;
import chain.pipeline.FinalizeOrderStage;
import chain.pipeline.OrderProcess;
import chain.pipeline.ProcessingPipeline;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PipelineConfiguration {
    @Bean
    public ProcessingPipeline<OrderProcess> orderPipeline() {
        return new ProcessingPipeline<OrderProcess>()
                .addLast(new CalculateSubtotalStage())
                .addLast(new ApplyDiscountStage())
                .addLast(new CalculateTaxStage())
                .addLast(new FinalizeOrderStage());
    }
}

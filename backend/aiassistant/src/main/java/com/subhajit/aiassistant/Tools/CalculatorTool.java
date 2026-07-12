package com.subhajit.aiassistant.Tools;


import com.subhajit.aiassistant.Configures.ToolRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;


import net.objecthunter.exp4j.ExpressionBuilder;
import java.util.function.Function;

@Configuration
public class CalculatorTool {

    @Bean
    @Description("Performs mathematical calculations on mathematical expressions")
    public Function<ToolRequest, String> executeCalculator() {
        return request -> {
            try {
                String expression = request.payload();

                if (expression == null || expression.isBlank()) {
                    return "Calculation failed: Expression is empty.";
                }

                expression = expression.trim();
                if (expression.startsWith("\"") && expression.endsWith("\"")) {
                    expression = expression.substring(1, expression.length() - 1);
                }

                String cleanExpression = expression.replaceAll("[^0-9\\+\\-\\*/\\(\\)\\.]", "");

                System.out.println("🧮 Tool executing math formula: " + cleanExpression);

                double result = new ExpressionBuilder(cleanExpression)
                        .build()
                        .evaluate();

                if (result % 1 == 0) {
                    return String.valueOf((long) result);
                }

                return String.valueOf(result);

            } catch (Exception e) {
                System.err.println("❌ Math parsing failure: " + e.getMessage());
                return "Calculation failed: " + e.getMessage();
            }
        };
    }
}

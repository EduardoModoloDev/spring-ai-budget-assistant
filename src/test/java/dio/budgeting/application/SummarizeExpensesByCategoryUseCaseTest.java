package dio.budgeting.application;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.support.ToolCallbacks;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SummarizeExpensesByCategoryUseCaseTest {
    @Mock
    TransactionRepository transactionRepository;

    @InjectMocks
    SummarizeExpensesByCategoryUseCase useCase;

    @Test
    void should_sumAmounts_when_categoryHasTransactions() {
        when(transactionRepository.findAllByCategory(Category.GROCERIES)).thenReturn(List.of(
                new Transaction("Mercado", 8000, Category.GROCERIES),
                new Transaction("Feira", 4050, Category.GROCERIES)));

        var summary = useCase.execute(Category.GROCERIES);

        assertThat(summary.category()).isEqualTo("GROCERIES");
        assertThat(summary.transactionCount()).isEqualTo(2);
        assertThat(summary.totalInCents()).isEqualTo(12050);
        assertThat(summary.totalInReais()).isEqualByComparingTo(new BigDecimal("120.50"));
    }

    @Test
    void should_returnZero_when_categoryHasNoTransactions() {
        when(transactionRepository.findAllByCategory(Category.AUTO)).thenReturn(List.of());

        var summary = useCase.execute(Category.AUTO);

        assertThat(summary.transactionCount()).isZero();
        assertThat(summary.totalInCents()).isZero();
        assertThat(summary.totalInReais()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // Simula o que o Spring AI faz no Tool Calling: descobre a ferramenta pelo @Tool
    // e a chama com os argumentos em JSON que o modelo enviaria. Não usa a OpenAI.
    @Test
    void should_beCallableAsTool_when_modelSendsJsonArguments() {
        when(transactionRepository.findAllByCategory(Category.PHARMA)).thenReturn(List.of(
                new Transaction("Remédio", 3990, Category.PHARMA)));

        var toolCallbacks = ToolCallbacks.from(useCase);

        assertThat(toolCallbacks).hasSize(1);
        var tool = toolCallbacks[0];
        assertThat(tool.getToolDefinition().name()).isEqualTo("summarize-expenses-by-category");
        assertThat(tool.getToolDefinition().description()).contains("TOTAL gasto");

        var result = tool.call("{\"category\": \"PHARMA\"}");

        assertThat(result)
                .contains("\"category\":\"PHARMA\"")
                .contains("\"transactionCount\":1")
                .contains("\"totalInCents\":3990")
                .contains("39.9");
    }
}

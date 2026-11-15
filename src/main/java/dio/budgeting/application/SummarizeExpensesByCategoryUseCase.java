package dio.budgeting.application;

import dio.budgeting.application.output.CategorySummaryOutput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class SummarizeExpensesByCategoryUseCase {
    private final TransactionRepository transactionRepository;

    public SummarizeExpensesByCategoryUseCase(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Tool(name = "summarize-expenses-by-category",
            description = """
                    Calcula o TOTAL gasto e a quantidade de transações de uma categoria. \
                    Use quando o usuário perguntar quanto gastou no total em uma categoria \
                    (ex.: "quanto gastei no mercado?", "qual o total com farmácia?"). \
                    Retorna o total em centavos (totalInCents) e em reais (totalInReais); responda ao usuário em reais.""")
    public CategorySummaryOutput execute(@ToolParam(description = "Categoria de uma transação") Category category) {
        var transactions = transactionRepository.findAllByCategory(category);

        long totalInCents = transactions.stream().mapToLong(Transaction::getAmount).sum();
        var totalInReais = BigDecimal.valueOf(totalInCents).movePointLeft(2);

        return new CategorySummaryOutput(category.name(), transactions.size(), totalInCents, totalInReais);
    }
}

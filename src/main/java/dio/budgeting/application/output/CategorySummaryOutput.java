package dio.budgeting.application.output;

import java.math.BigDecimal;

/**
 * Resumo de gastos de uma categoria.
 *
 * @param category         categoria consultada
 * @param transactionCount quantidade de transações encontradas
 * @param totalInCents     soma dos valores, em centavos (mesma unidade usada ao salvar)
 * @param totalInReais     mesma soma convertida para reais (ex.: 15050 centavos -> 150.50)
 */
public record CategorySummaryOutput(String category, long transactionCount, long totalInCents, BigDecimal totalInReais) {
}

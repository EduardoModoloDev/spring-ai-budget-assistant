package dio.budgeting.domain;

/**
 * Lançada quando alguém tenta criar uma transação que quebra as regras do domínio
 * (ex.: valor menor ou igual a zero). Vale tanto para a API REST quanto para o Tool Calling da IA.
 */
public class InvalidTransactionException extends RuntimeException {
    public InvalidTransactionException(String message) {
        super(message);
    }
}

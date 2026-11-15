package dio.budgeting.infrastructure.http.response;

import java.util.Map;

/**
 * Formato padrão de erro devolvido pela API.
 *
 * @param status  código HTTP (ex.: 400)
 * @param message mensagem geral, em português
 * @param errors  erros por campo (ex.: "amount" -> "O valor deve ser maior que zero"); pode vir vazio
 */
public record ErrorResponse(int status, String message, Map<String, String> errors) {
    public static ErrorResponse of(int status, String message) {
        return new ErrorResponse(status, message, Map.of());
    }
}

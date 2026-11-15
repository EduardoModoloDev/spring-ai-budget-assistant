package dio.budgeting.infrastructure.http;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.InvalidTransactionException;
import dio.budgeting.infrastructure.http.response.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centraliza o tratamento de erros dos controllers e transforma exceções em respostas JSON claras.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final String VALID_CATEGORIES = Arrays.toString(Category.values());

    // Bean Validation falhou no corpo da requisição (@Valid TransactionRequest)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Dados da transação inválidos.", errors);
    }

    // Regra de negócio violada dentro do domínio
    @ExceptionHandler(InvalidTransactionException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidTransaction(InvalidTransactionException ex) {
        return ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
    }

    // JSON malformado ou categoria que não existe no enum (ex.: "category": "LAZER")
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ErrorResponse.of(HttpStatus.BAD_REQUEST.value(),
                "Corpo da requisição inválido. Verifique o JSON e use uma categoria válida: " + VALID_CATEGORIES + ".");
    }

    // Categoria inválida na URL (ex.: GET /transactions/LAZER)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ErrorResponse.of(HttpStatus.BAD_REQUEST.value(),
                "Valor inválido '" + ex.getValue() + "' para '" + ex.getName() + "'. Categorias aceitas: " + VALID_CATEGORIES + ".");
    }
}

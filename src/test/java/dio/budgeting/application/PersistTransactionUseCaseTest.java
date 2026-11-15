package dio.budgeting.application;

import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.InvalidTransactionException;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersistTransactionUseCaseTest {
    @Mock
    TransactionRepository transactionRepository;

    @InjectMocks
    PersistTransactionUseCase useCase;

    @Test
    void should_saveTransaction_when_inputIsValid() {
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var output = useCase.execute(new PersistTransactionInput("Farmácia", 4000, Category.PHARMA));

        assertThat(output.description()).isEqualTo("Farmácia");
        assertThat(output.category()).isEqualTo("PHARMA");
        verify(transactionRepository).save(any(Transaction.class));
    }

    // Mesmo que a IA (Tool Calling) mande um valor inválido, nada é salvo no banco
    @Test
    void should_notSave_when_amountIsInvalid() {
        var input = new PersistTransactionInput("Farmácia", -4000, Category.PHARMA);

        assertThatThrownBy(() -> useCase.execute(input)).isInstanceOf(InvalidTransactionException.class);
        verify(transactionRepository, never()).save(any());
    }
}

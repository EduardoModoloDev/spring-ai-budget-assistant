package dio.budgeting.infrastructure.http;

import dio.budgeting.application.ListTransactionsByCategoryUseCase;
import dio.budgeting.application.PersistTransactionUseCase;
import dio.budgeting.application.SummarizeExpensesByCategoryUseCase;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa a camada HTTP com MockMvc. Os casos de uso são reais; o banco (repositório) e todos os
 * modelos de IA (transcrição, chat e voz) são mocks: nenhum teste chama a OpenAI nem precisa de internet.
 */
@WebMvcTest(TransactionController.class)
@Import({PersistTransactionUseCase.class, ListTransactionsByCategoryUseCase.class, SummarizeExpensesByCategoryUseCase.class})
class TransactionControllerTest {
    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    TransactionRepository transactionRepository;

    @MockitoBean
    TranscriptionModel transcriptionModel;

    @MockitoBean
    TextToSpeechModel textToSpeechModel;

    @MockitoBean
    ChatModel chatModel;

    // O controller recebe um ChatClient.Builder; aqui ele é real, mas usa o ChatModel mockado
    @TestConfiguration
    static class ChatClientTestConfig {
        @Bean
        ChatClient.Builder chatClientBuilder(ChatModel chatModel) {
            return ChatClient.builder(chatModel);
        }
    }

    @Test
    void should_return201_when_transactionIsValid() throws Exception {
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "Gasolina", "category": "AUTO", "amount": 15000}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Gasolina"))
                .andExpect(jsonPath("$.category").value("AUTO"));
    }

    @Test
    void should_return400WithFieldErrors_when_transactionIsInvalid() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "  ", "amount": -5}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Dados da transação inválidos."))
                .andExpect(jsonPath("$.errors.description").value("A descrição é obrigatória."))
                .andExpect(jsonPath("$.errors.amount").value("O valor deve ser maior que zero (em centavos)."))
                .andExpect(jsonPath("$.errors.category").exists());

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void should_return400_when_categoryDoesNotExist() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "Cinema", "category": "LAZER", "amount": 3000}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("GROCERIES, PHARMA, AUTO")));
    }

    @Test
    void should_return400_when_categoryInUrlDoesNotExist() throws Exception {
        mockMvc.perform(get("/transactions/LAZER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Valor inválido 'LAZER'")));
    }

    @Test
    void should_listTransactions_when_categoryIsValid() throws Exception {
        when(transactionRepository.findAllByCategory(Category.PHARMA))
                .thenReturn(List.of(new Transaction("Remédio", 3990, Category.PHARMA)));

        mockMvc.perform(get("/transactions/PHARMA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].description").value("Remédio"));
    }

    @Test
    void should_returnSummary_when_categoryHasTransactions() throws Exception {
        when(transactionRepository.findAllByCategory(Category.GROCERIES)).thenReturn(List.of(
                new Transaction("Mercado", 8000, Category.GROCERIES),
                new Transaction("Padaria", 1550, Category.GROCERIES)));

        mockMvc.perform(get("/transactions/GROCERIES/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("GROCERIES"))
                .andExpect(jsonPath("$.transactionCount").value(2))
                .andExpect(jsonPath("$.totalInCents").value(9550))
                .andExpect(jsonPath("$.totalInReais").value(95.50));
    }

    // Fluxo completo do /ai com tudo mockado: áudio -> texto -> chat (com ferramentas) -> voz
    @Test
    void should_returnAudio_when_audioIsSentToAiEndpoint() throws Exception {
        byte[] fakeMp3 = {1, 2, 3, 4};
        when(transcriptionModel.transcribe(any(Resource.class))).thenReturn("Quanto gastei no mercado?");
        when(chatModel.getDefaultOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage("Você gastou 95 reais no mercado.")))));
        when(textToSpeechModel.call("Você gastou 95 reais no mercado.")).thenReturn(fakeMp3);

        var audio = new MockMultipartFile("file", "pergunta.m4a", "audio/m4a", new byte[]{9, 9, 9});

        mockMvc.perform(multipart("/transactions/ai").file(audio))
                .andExpect(status().isOk())
                .andExpect(content().bytes(fakeMp3));

        // Confere que o texto transcrito foi enviado à IA junto com as ferramentas registradas (incluindo a nova)
        var promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        var prompt = promptCaptor.getValue();
        assertThat(prompt.getUserMessage().getText()).isEqualTo("Quanto gastei no mercado?");

        var options = (ToolCallingChatOptions) prompt.getOptions();
        assertThat(options.getToolCallbacks())
                .extracting(ToolCallback::getToolDefinition)
                .extracting(definition -> definition.name())
                .contains("persist-transaction", "list-transactions-by-category", "summarize-expenses-by-category");
    }
}

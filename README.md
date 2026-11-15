# Spring AI Budget Assistant

API de controle de gastos com **Spring Boot** e **Spring AI**, feita no Desafio de Projeto da DIO. Você manda um **áudio** como "gastei 80 reais no mercado" e a API registra a transação. Você também pode perguntar "quanto gastei no mercado?" e ouvir a resposta.

<!-- Espaço para prints: coloque as imagens em docs/ e descomente as linhas abaixo -->
<!-- ![Requisição no Postman](docs/print-postman.png) -->
<!-- ![Testes passando](docs/print-testes.png) -->

---

## O que o projeto faz

O fluxo principal acontece no endpoint `POST /transactions/ai`:

```
Áudio (m4a/mp3) ──► Transcrição (Whisper) ──► Texto
                                              │
                                              ▼
                              ChatClient + modelo de IA (gpt-4o-mini)
                              entende a intenção e escolhe uma ferramenta
                                              │
                ┌─────────────────────────────┼──────────────────────────────┐
                ▼                             ▼                              ▼
     persist-transaction        list-transactions-by-category    summarize-expenses-by-category
     (salva um gasto)           (lista gastos da categoria)      (total gasto na categoria) ★ nova
                └─────────────────────────────┼──────────────────────────────┘
                                              ▼
                              Resposta em texto ──► Voz (TTS) ──► audio.mp3
```

1. O cliente envia um arquivo de áudio.
2. O `TranscriptionModel` transforma o áudio em texto.
3. O `ChatClient` manda o texto para a IA junto com a lista de **ferramentas** (métodos Java anotados com `@Tool`).
4. A IA decide qual ferramenta chamar (isso se chama **Tool Calling**). O Spring AI executa o método Java de verdade, que salva ou consulta o banco.
5. A IA escreve a resposta final, e o `TextToSpeechModel` a transforma em um MP3.

Também existem endpoints REST "normais", sem IA, para criar e consultar transações.

### Arquitetura (camadas)

| Pasta | Responsabilidade |
|---|---|
| `domain` | Regras de negócio: `Transaction`, `Category`, contrato do repositório |
| `application` | Casos de uso. São usados pela API REST **e** como ferramentas da IA (`@Tool`) |
| `infrastructure/http` | Controller REST, requests/responses e tratamento de erros |
| `infrastructure/persistence` | JPA: entidade e repositório do banco |

## Tecnologias utilizadas

- **Java 25** (o Gradle baixa sozinho, se precisar)
- **Spring Boot 4.0** (Web MVC, Data JPA, Validation)
- **Spring AI 2.0** com OpenAI: `ChatClient`, Tool Calling (`@Tool`), transcrição (`whisper-1`) e voz (`gpt-4o-mini-tts`)
- **MySQL** via Docker Compose (padrão) ou **H2** em memória (perfil `local`)
- **Gradle**, **Lombok**
- **JUnit 5**, **Mockito**, **MockMvc** e **AssertJ** nos testes

## Como executar

### Pré-requisitos

- Um JDK 17 ou mais novo instalado só para rodar o Gradle. O projeto compila com Java 25, que o Gradle baixa automaticamente.
- Uma chave da OpenAI, para os recursos de IA: https://platform.openai.com/api-keys
- Docker, **opcional**. Só é necessário se você quiser usar o MySQL.

### Variáveis de ambiente

| Variável | Para quê |
|---|---|
| `OPENAI_API_KEY` | Chave da OpenAI. **Nunca** coloque a chave no código; o `application.properties` lê `${OPENAI_API_KEY}` |

Veja o modelo em [`.env.example`](.env.example).

```bash
# Linux/macOS/Git Bash
export OPENAI_API_KEY="sua_chave_aqui"
```

```powershell
# Windows PowerShell
$env:OPENAI_API_KEY="sua_chave_aqui"
```

### Opção 1: sem Docker (H2 em memória)

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

Os dados ficam só na memória e somem quando a aplicação para.

### Opção 2: com Docker (MySQL)

Com o Docker aberto:

```bash
./gradlew bootRun
```

O Spring Boot sobe o MySQL do `compose.yml` sozinho.

A API fica em `http://localhost:8080`.

### Rodar os testes

```bash
./gradlew test
```

Os testes **não** usam a OpenAI nem a internet: os modelos de IA são mockados e o banco é H2. Os testes de integração do projeto original (`*IT.java`) só rodam se `OPENAI_API_KEY` estiver definida; sem ela, eles são pulados.

## Melhorias implementadas

### A) Validação das transações + erros claros

**Problema:** no projeto original, `POST /transactions` com descrição vazia e valor negativo era salvo normalmente (HTTP 201).

**O que mudou:**

- [`TransactionRequest`](src/main/java/dio/budgeting/infrastructure/http/request/TransactionRequest.java): anotações de **Bean Validation** (`@NotBlank`, `@NotNull`, `@Positive`, `@Size`) com mensagens em português. O controller usa `@Valid` para ativá-las.
- [`Transaction`](src/main/java/dio/budgeting/domain/Transaction.java): o próprio domínio valida descrição, valor e categoria e lança [`InvalidTransactionException`](src/main/java/dio/budgeting/domain/InvalidTransactionException.java). Isso é importante porque a **IA não passa pelo controller**: quando ela chama a ferramenta `persist-transaction`, a regra de negócio continua protegida.
- [`GlobalExceptionHandler`](src/main/java/dio/budgeting/infrastructure/http/GlobalExceptionHandler.java) (`@RestControllerAdvice`): transforma as exceções em respostas JSON padronizadas ([`ErrorResponse`](src/main/java/dio/budgeting/infrastructure/http/response/ErrorResponse.java)) com status 400. Trata erros de validação, regra de negócio, JSON inválido/categoria inexistente e categoria inválida na URL.

### B) Nova ferramenta de Tool Calling: total gasto por categoria

**Problema:** a IA só conseguia salvar e listar gastos. Para responder "quanto gastei no mercado?", ela teria que somar a lista sozinha, o que é mais caro e sujeito a erro.

**O que mudou:**

- [`SummarizeExpensesByCategoryUseCase`](src/main/java/dio/budgeting/application/SummarizeExpensesByCategoryUseCase.java): novo caso de uso com `@Tool(name = "summarize-expenses-by-category")`. A descrição do `@Tool` explica para a IA **quando** usá-lo. A soma é feita em Java, de forma exata, e o resultado vem em centavos e em reais ([`CategorySummaryOutput`](src/main/java/dio/budgeting/application/output/CategorySummaryOutput.java)).
- [`TransactionController`](src/main/java/dio/budgeting/infrastructure/http/TransactionController.java): a ferramenta foi registrada no `ChatClient` (`defaultTools`) e ganhou um endpoint REST: `GET /transactions/{category}/summary`.
- [`system-message.st`](src/main/resources/prompts/system-message.st): uma linha a mais orienta a IA a usar o resumo e responder em reais.

### Outras mudanças de configuração

- [`application-local.properties`](src/main/resources/application-local.properties): perfil `local` com H2, para rodar sem Docker.
- [`application-test.properties`](src/test/resources/application-test.properties): perfil usado pelos testes, com H2 e sem chave real.
- [`settings.gradle`](settings.gradle): plugin `foojay-resolver`, que baixa o JDK 25 automaticamente se ele não estiver instalado.
- `.gitignore` e `.env.example`: evitam subir segredos, logs e áudios gerados.

### Testes criados

| Teste | O que verifica |
|---|---|
| [`TransactionTest`](src/test/java/dio/budgeting/domain/TransactionTest.java) | Regras de validação do domínio |
| [`PersistTransactionUseCaseTest`](src/test/java/dio/budgeting/application/PersistTransactionUseCaseTest.java) | Valor inválido vindo da IA não é salvo (Mockito) |
| [`SummarizeExpensesByCategoryUseCaseTest`](src/test/java/dio/budgeting/application/SummarizeExpensesByCategoryUseCaseTest.java) | Soma correta e chamada da ferramenta com JSON, como o Spring AI faz |
| [`TransactionControllerTest`](src/test/java/dio/budgeting/infrastructure/http/TransactionControllerTest.java) | Endpoints REST com MockMvc, erros 400 e o fluxo `/ai` completo com transcrição, chat e voz mockados |

## Como testar o fluxo principal

Com a aplicação rodando:

**Criar uma transação** (o valor é em **centavos**: 8000 = R$ 80,00)

```bash
curl -X POST http://localhost:8080/transactions -H "Content-Type: application/json" -d '{"description":"Mercado","category":"GROCERIES","amount":8000}'
```

**Listar por categoria** (`GROCERIES`, `PHARMA` ou `AUTO`)

```bash
curl http://localhost:8080/transactions/GROCERIES
```

**Total gasto na categoria** (melhoria B)

```bash
curl http://localhost:8080/transactions/GROCERIES/summary
```

```json
{"category":"GROCERIES","transactionCount":2,"totalInCents":9550,"totalInReais":95.50}
```

**Transação inválida** (melhoria A)

```bash
curl -X POST http://localhost:8080/transactions -H "Content-Type: application/json" -d '{"description":"","category":"AUTO","amount":-5}'
```

```json
{"status":400,"message":"Dados da transação inválidos.","errors":{"description":"A descrição é obrigatória.","amount":"O valor deve ser maior que zero (em centavos)."}}
```

**Fluxo com IA: áudio entra, áudio sai** (precisa de `OPENAI_API_KEY`)

```bash
curl -X POST http://localhost:8080/transactions/ai -F "file=@src/test/resources/audio/recording-1.m4a" --output resposta.mp3
```

Abra o `resposta.mp3` para ouvir a resposta. Grave também um áudio perguntando "quanto eu gastei no mercado?" para ver a nova ferramenta em ação.

## O que aprendi

<!-- Eduardo: escreva aqui com suas palavras -->

## Créditos

Projeto base: módulo `05-spring-ai` da trilha [dio-spring-boot-learning-track](https://github.com/digitalinnovationone/dio-spring-boot-learning-track), da [DIO](https://www.dio.me/). As melhorias, os testes e esta documentação foram adicionados por Eduardo Modolo.

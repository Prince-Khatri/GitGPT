package com.genai.gitgpt.rag.retrieve;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

@Service
public class AnswerGenerator {

    private static final String SYSTEM = """
            You are GitGPT. Answer questions about one indexed GitHub repository snapshot.
            Use ONLY the numbered chunks. If they are missing or weak, say the indexed snapshot does not contain enough information.
            Prior conversation is only to interpret follow-ups (pronouns, "that class", "the same file").
            Do not treat prior answers as source of truth. Do not invent files, APIs, classes, or paths.

            Write GitHub-flavored markdown that is easy to scan:
            - Start with one short opening sentence.
            - Use bullet lists (`- `) or ### headings for files, workflows, features, or steps.
            - Put paths and filenames in backticks (`README.md`, `.github/workflows/snake.yml`).
            - Keep paragraphs short. Never glue several topics into one paragraph.

            Citations:
            - Cite ONLY as [1] or [1,2] using the chunk numbers.
            - Put the citation right after the claim it supports.
            - Never write [path:12-40], [ path.yml:1-2 ], or file paths inside brackets.

            Match the repository's real languages and folder layout.
            Never mention, quote, or request access tokens or secrets.
            """;

    public String generate(
            ChatModel chatModel,
            String question,
            String intent,
            String packedContext,
            List<ChatTurn> history
    ) {
        return chatModel.call(prompt(question, intent, packedContext, history)).getResult().getOutput().getText();
    }

    public void stream(
            ChatModel chatModel,
            String question,
            String intent,
            String packedContext,
            List<ChatTurn> history,
            Consumer<String> onDelta
    ) {
        Flux<ChatResponse> flux = chatModel.stream(prompt(question, intent, packedContext, history));
        flux.doOnNext(response -> {
            String text = text(response);
            if (text != null && !text.isEmpty()) {
                onDelta.accept(text);
            }
        }).blockLast(Duration.ofSeconds(90));
    }

    private static Prompt prompt(String question, String intent, String packedContext, List<ChatTurn> history) {
        StringBuilder user = new StringBuilder();
        if (history != null && !history.isEmpty()) {
            user.append("Prior conversation (follow-up context only):\n");
            for (ChatTurn turn : history) {
                user.append(turn.role()).append(": ").append(turn.content()).append('\n');
            }
            user.append('\n');
        }
        user.append("Intent: ").append(intent == null ? "" : intent).append('\n');
        user.append("Question: ").append(question).append("\n\n");
        user.append("Indexed chunks (cite as [1], [2], [1,2] only):\n").append(packedContext);
        user.append("\n\nRemember: markdown bullets, backticks for paths, citations like [1] never [path:lines].");
        return new Prompt(List.of(
                new SystemMessage(SYSTEM),
                new UserMessage(user.toString())
        ));
    }

    public record ChatTurn(String role, String content) {
    }

    private static String text(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }
}

package com.genai.gitgpt.rag.retrieve;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AnswerGenerator {

    private static final String SYSTEM = """
            You are GitGPT. Answer questions about one indexed GitHub repository snapshot.
            Use ONLY the provided chunks. If they are missing or weak, say the indexed snapshot does not contain enough information.
            Do not invent files, APIs, classes, or paths.
            Cite evidence as [path:start-end].
            Match the repository's real languages and folder layout.
            Never mention, quote, or request access tokens or secrets.
            """;

    private final ChatModel chatModel;

    public String generate(String question, String intent, String packedContext) {
        String user = """
                Intent: %s
                Question: %s

                Indexed chunks:
                %s
                """.formatted(intent, question, packedContext);
        return chatModel.call(new Prompt(List.of(
                new SystemMessage(SYSTEM),
                new UserMessage(user)
        ))).getResult().getOutput().getText();
    }
}

package com.genai.gitgpt.rag.retrieve;

import com.genai.gitgpt.rag.config.AskProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetrievalHelpersTest {

    @Test
    void extractsIdentifiersAndPaths() {
        List<String> found = IdentifierExtractor.extract("Where does `UserService` save accessToken in src/UserService.java?");
        assertTrue(found.contains("UserService"));
        assertTrue(found.contains("accessToken"));
        assertTrue(found.stream().anyMatch(value -> value.contains("UserService.java")));
    }

    @Test
    void fallbackPlanKeepsTheQuestion() {
        QueryPlan plan = QueryPlan.fallback("Explain OAuth2AuthenticationSuccessHandler");
        assertEquals("explain", plan.intent());
        assertTrue(plan.symbolHints().contains("OAuth2AuthenticationSuccessHandler"));
        assertEquals("Explain OAuth2AuthenticationSuccessHandler", plan.rewrittenQuery());
    }

    @Test
    void skipsPlannerWhenIdentifiersArePresent() {
        assertTrue(QueryPlanner.hasStrongIdentifiers("Where is UserService?"));
        assertFalse(QueryPlanner.hasStrongIdentifiers("how does login work?"));
    }

    @Test
    void githubBlobLinkPointsAtTheExactLines() {
        String url = GitHubLinks.blob("octo/gitgpt", "abc123", "src/UserService.java", 10, 20);
        assertEquals("https://github.com/octo/gitgpt/blob/abc123/src/UserService.java#L10-L20", url);
    }

    @Test
    void followUpUsesThePreviousQuestionWhenTheLatestHasNoNames() {
        String retrieval = FollowUpQuery.forRetrieval(
                List.of("Where is UserService?"),
                "where is that used?"
        );
        assertTrue(retrieval.contains("UserService"));
        assertTrue(retrieval.contains("where is that used?"));
        assertEquals("Explain the login filter", FollowUpQuery.forRetrieval(List.of(), "Explain the login filter"));
        assertEquals("Find RepoAskService", FollowUpQuery.forRetrieval(List.of("old"), "Find RepoAskService"));
    }

    @Test
    void packerDropsOverlappingWindowsAndCapsCount() {
        AskProperties properties = new AskProperties();
        properties.setPackedChunks(2);
        properties.setMaxContextTokens(10_000);
        ContextPacker packer = new ContextPacker(properties);
        RetrievedChunk first = new RetrievedChunk("1", "A.java", "java", 1, 40, "sha", "one", "KEYWORD", 2.0);
        RetrievedChunk overlap = new RetrievedChunk("2", "A.java", "java", 20, 60, "sha", "two", "VECTOR", 0.4);
        RetrievedChunk other = new RetrievedChunk("3", "B.java", "java", 1, 20, "sha", "three", "VECTOR", 0.9);
        List<RetrievedChunk> packed = packer.pack(List.of(first), List.of(overlap, other));
        assertEquals(2, packed.size());
        assertEquals("A.java", packed.get(0).path());
        assertEquals("B.java", packed.get(1).path());
        assertFalse(packed.stream().anyMatch(chunk -> "2".equals(chunk.id())));
    }
}

package com.trackai.backend.service;

import com.trackai.backend.dto.groq.GroqMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatResponsePolicyTest {

    @Test
    void followsExactAcknowledgementInstructionFromLatestMessage() {
        String message = "bro english mein likhna abhi bass smjh gaye to yes likho";

        assertThat(ChatResponsePolicy.forcedReply(message)).contains("Yes");
    }

    @Test
    void normalQuestionIsNotReplacedWithAcknowledgement() {
        assertThat(ChatResponsePolicy.forcedReply("Can you explain OAuth login?"))
                .isEmpty();
    }

    @Test
    void latestUserInstructionWinsOverLargeEarlierAnswer() {
        List<GroqMessage> messages = List.of(
                new GroqMessage("user", "Create a complete website"),
                new GroqMessage("assistant", "Large generated page ".repeat(2_000)),
                new GroqMessage("user", "Reply only okay"));

        assertThat(ChatResponsePolicy.forcedReply(ChatResponsePolicy.latestUserMessage(messages)))
                .contains("Okay");
        assertThat(ChatResponsePolicy.recommendedMaxOutputTokens(messages, 8_192))
                .isEqualTo(16);
    }

    @Test
    void shortFollowUpGetsSmallOutputBudget() {
        List<GroqMessage> messages = List.of(new GroqMessage("user", "dolink phir"));

        assertThat(ChatResponsePolicy.recommendedMaxOutputTokens(messages, 8_192))
                .isEqualTo(384);
    }

    @Test
    void explicitImplementationRequestCanUseFullConfiguredBudget() {
        List<GroqMessage> messages = List.of(
                new GroqMessage("user", "Create a complete React website page with working code"));

        assertThat(ChatResponsePolicy.recommendedMaxOutputTokens(messages, 8_192))
                .isEqualTo(8_192);
    }
}
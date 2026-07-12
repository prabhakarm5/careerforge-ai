package com.trackai.backend.service.impl;

import com.trackai.backend.entity.ChatMessage;
import com.trackai.backend.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * "Lightweight RAG" â€” this is intentionally NOT a vector/embeddings
 * pipeline. It's a cheap keyword-overlap retrieval over the conversation's
 * own message history, used ONLY when the user's message shows recall
 * intent ("remember", "yaad hai", "pehle bataya tha", etc).
 *
 * Why keyword-based instead of embeddings:
 * - No new infra (no pgvector, no embeddings API calls, no extra latency
 * on every single message).
 * - Works fine at conversation-history scale (dozens/hundreds of
 * messages per conversation, not millions of documents).
 * - Zero added token cost for the 95%+ of messages that never ask to
 * recall anything.
 *
 * If you later need true semantic recall (paraphrased recall, e.g. user
 * asks about something without using the same words), swap
 * retrieveRelevant()'s implementation for a pgvector similarity query â€”
 * the call site in ChatServiceImpl doesn't need to change.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryRetrievalService {

    private final ChatMessageRepository chatMessageRepository;

    // How many older messages we scan for keyword matches per conversation.
    private static final int SCAN_WINDOW = 200;

    // How many matched snippets we're willing to inject into context.
    private static final int MAX_SNIPPETS = 5;

    // How much of each matched message we include (keeps token cost low).
    private static final int SNIPPET_MAX_CHARS = 300;

    private static final Pattern RECALL_PATTERN_EN = Pattern.compile(
            "(?i)\\b(remember|recall|earlier|before|previously|you said|we discussed|"
                    + "last time|what did i (say|tell|ask)|mentioned earlier|"
                    + "we talked about|going back to)\\b");

    private static final Pattern RECALL_PATTERN_HI = Pattern.compile(
            "(?i)(yaad|pehle|purana|purani|bataya tha|kaha tha|kahaa tha|"
                    + "humne baat ki thi|hum log|hmlog|hamlog|kya baat kiye|maine bola tha|maine kaha tha|"
                    + "pichhla|pichla|pichhli baar|wo jo maine|jo maine bataya)");

    private static final Set<String> STOPWORDS = Set.of(
            "what", "when", "where", "which", "about", "there", "their", "would",
            "could", "should", "again", "please", "tell", "with", "that", "this",
            "kya", "kaha", "kaise", "mujhe", "mera", "meri", "tumne", "kripya",
            "hoga", "hota", "karo", "karna", "bhai", "wala", "wali");

    /**
     * Cheap, fast check â€” regex only, no DB call. Call this first before
     * doing any retrieval work.
     */
    public boolean isRecallIntent(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        return RECALL_PATTERN_EN.matcher(message).find()
                || RECALL_PATTERN_HI.matcher(message).find();
    }

    /**
     * Only call this after isRecallIntent() returns true. Returns short
     * snippets of older messages from THIS conversation that share
     * meaningful keywords with the current query, most recent first.
     */
    public List<String> retrieveRelevant(String conversationId, String query) {
        Set<String> keywords = extractKeywords(query);
        if (keywords.isEmpty()) {
            return List.of();
        }

        List<ChatMessage> candidates = chatMessageRepository
                .findByConversationIdOrderByCreatedAtDesc(
                        conversationId, PageRequest.of(0, SCAN_WINDOW));

        return candidates.stream()
                .filter(m -> containsAnyKeyword(m.getContent(), keywords))
                .limit(MAX_SNIPPETS)
                .map(m -> "[" + m.getRole() + "] " + truncate(m.getContent(), SNIPPET_MAX_CHARS))
                .collect(Collectors.toList());
    }

    private Set<String> extractKeywords(String text) {
        if (text == null) {
            return Set.of();
        }
        return Arrays.stream(text.toLowerCase().split("\\W+"))
                .filter(w -> w.length() > 3)
                .filter(w -> !STOPWORDS.contains(w))
                .collect(Collectors.toSet());
    }

    private boolean containsAnyKeyword(String content, Set<String> keywords) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String lower = content.toLowerCase();
        return keywords.stream().anyMatch(lower::contains);
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
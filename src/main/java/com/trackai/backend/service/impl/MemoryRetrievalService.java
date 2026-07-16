package com.trackai.backend.service.impl;

import com.trackai.backend.entity.ChatMessage;
import com.trackai.backend.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Lightweight semantic memory for one conversation. It converts messages into local
 * term-frequency vectors and ranks older messages with cosine similarity. This keeps
 * recall durable across Redis expiry and app restarts without an embeddings API call
 * or a separate vector database on every chat request.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryRetrievalService {

    private final ChatMessageRepository chatMessageRepository;

    private static final int SCAN_WINDOW = 600;
    private static final int MAX_SNIPPETS = 10;
    private static final int SNIPPET_MAX_CHARS = 700;
    private static final double MIN_SIMILARITY = 0.10;

    private static final Pattern RECALL_PATTERN_EN = Pattern.compile(
            "(?i)\\b(remember|recall|earlier|before|previously|you said|we discussed|last time|"
                    + "what did i (say|tell|ask)|mentioned earlier|we talked about|going back to|"
                    + "continue that|same one|same thing|as discussed)\\b");

    private static final Pattern RECALL_PATTERN_HI = Pattern.compile(
            "(?i)(yaad|pehle|purana|purani|bataya tha|kaha tha|kahaa tha|humne baat ki thi|"
                    + "hum log|hmlog|hamlog|kya baat kiye|maine bola tha|maine kaha tha|pichhla|"
                    + "pichla|pichhli baar|wo jo maine|jo maine bataya|ussi ko|usi ko|wahi wala|"
                    + "same wala|continue karo|aage karo|bhool|bhul|yaad nahi|yaad nhii)");

    private static final Set<String> STOPWORDS = Set.of(
            "what", "when", "where", "which", "about", "there", "their", "would", "could",
            "should", "again", "please", "tell", "with", "that", "this", "from", "have",
            "kya", "kaha", "kaise", "mujhe", "mera", "meri", "tumne", "kripya", "hoga",
            "hota", "karo", "karna", "bhai", "wala", "wali", "hain", "nahi");

    public boolean isRecallIntent(String message) {
        if (message == null || message.isBlank()) return false;
        return RECALL_PATTERN_EN.matcher(message).find() || RECALL_PATTERN_HI.matcher(message).find();
    }

    public List<String> retrieveRelevant(String conversationId, String query) {
        Map<String, Double> queryVector = vectorize(query);
        if (queryVector.isEmpty()) return List.of();

        return chatMessageRepository
                .findByConversationIdOrderByCreatedAtDesc(
                        conversationId, PageRequest.of(0, SCAN_WINDOW))
                .stream()
                .filter(message -> message.getContent() != null)
                .filter(message -> !message.getContent().trim().equalsIgnoreCase(query.trim()))
                .map(message -> new ScoredMessage(
                        message, cosine(queryVector, vectorize(message.getContent()))))
                .filter(item -> item.score() >= MIN_SIMILARITY)
                .sorted(Comparator.comparingDouble(ScoredMessage::score).reversed())
                .limit(MAX_SNIPPETS)
                .map(item -> "[" + item.message().getRole() + "] "
                        + truncate(item.message().getContent(), SNIPPET_MAX_CHARS))
                .collect(Collectors.toList());
    }

    private Map<String, Double> vectorize(String text) {
        Map<String, Double> vector = new HashMap<>();
        if (text == null || text.isBlank()) return vector;
        Arrays.stream(text.toLowerCase().split("[^\\p{L}\\p{N}]+"))
                .filter(word -> word.length() > 2)
                .filter(word -> !STOPWORDS.contains(word))
                .forEach(word -> vector.merge(word, 1.0, Double::sum));
        return vector;
    }

    private double cosine(Map<String, Double> left, Map<String, Double> right) {
        if (left.isEmpty() || right.isEmpty()) return 0;
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (double value : left.values()) leftNorm += value * value;
        for (double value : right.values()) rightNorm += value * value;
        for (Map.Entry<String, Double> entry : left.entrySet()) {
            dot += entry.getValue() * right.getOrDefault(entry.getKey(), 0.0);
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    private record ScoredMessage(ChatMessage message, double score) {
    }
}
package com.trackai.backend.service;

import com.trackai.backend.dto.groq.GroqMessage;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatResponsePolicy {

    private static final Pattern FORCED_REPLY = Pattern.compile(
            "(?i)(?:\\b(?:only|just|bas|bass|sirf)\\s+(yes|no|ok|okay|done)\\b"
                    + "|\\b(?:yes|no|ok|okay|done)\\s+(?:only|likho|bolo|reply|answer)\\b"
                    + "|\\b(?:if you understand|understood|samajh gaye|samajh gaya|smjh gaye|smjh gaya)"
                    + "[^.!?]{0,45}?\\b(?:say|write|reply|likho|bolo)?\\s*(yes|no|ok|okay|done)\\b)");

    private static final Pattern DETAILED_WORK = Pattern.compile(
            "(?i)\\b(?:create|build|generate|implement|write|design|code|page|app|website|project|"
                    + "complete|full|detailed|explain in detail|step by step|banao|bana do|code do|"
                    + "html|css|javascript|typescript|react|resume|document|file|source)\\b");

    private static final Pattern SHORT_REQUEST = Pattern.compile(
            "(?i)\\b(?:short|brief|concise|one line|one sentence|few words|chhota|chota|kam words)\\b");

    private ChatResponsePolicy() {
    }

    public static Optional<String> forcedReply(String message) {
        String latest = latestInstruction(message);
        Matcher matcher = FORCED_REPLY.matcher(latest);
        if (!matcher.find()) return Optional.empty();
        String requested = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        if (requested == null) return Optional.empty();
        return Optional.of(switch (requested.toLowerCase(Locale.ROOT)) {
            case "ok", "okay" -> "Okay";
            case "done" -> "Done";
            case "no" -> "No";
            default -> "Yes";
        });
    }

    public static int recommendedMaxOutputTokens(List<GroqMessage> messages, int hardMaximum) {
        String latest = latestUserMessage(messages);
        int desired;
        if (forcedReply(latest).isPresent()) {
            desired = 16;
        } else if (SHORT_REQUEST.matcher(latest).find()) {
            desired = 256;
        } else if (latest.length() <= 100 && !DETAILED_WORK.matcher(latest).find()) {
            desired = 384;
        } else if (DETAILED_WORK.matcher(latest).find()) {
            desired = hardMaximum;
        } else {
            desired = 1200;
        }
        return Math.max(16, Math.min(hardMaximum, desired));
    }

    public static boolean isDetailedWork(String message) {
        return DETAILED_WORK.matcher(latestInstruction(message)).find();
    }

    public static String resolveResponseStyle(String requestedStyle, String latestMessage) {
        String normalized = requestedStyle == null
                ? "auto"
                : requestedStyle.trim().toLowerCase(Locale.ROOT);

        if ("concise".equals(normalized)
                || "balanced".equals(normalized)
                || "detailed".equals(normalized)) {
            return normalized;
        }

        return isDetailedWork(latestMessage) ? "detailed" : "concise";
    }

    public static String latestUserMessage(List<GroqMessage> messages) {
        if (messages == null) return "";
        for (int index = messages.size() - 1; index >= 0; index--) {
            GroqMessage message = messages.get(index);
            if (message != null && "user".equalsIgnoreCase(message.getRole())) {
                return latestInstruction(message.getContent());
            }
        }
        return "";
    }

    private static String latestInstruction(String message) {
        if (message == null) return "";
        int attachmentEnd = message.lastIndexOf("[[/ATTACHMENT]]");
        String value = attachmentEnd >= 0
                ? message.substring(attachmentEnd + "[[/ATTACHMENT]]".length())
                : message;
        return value.replaceAll("\\s+", " ").trim();
    }
}

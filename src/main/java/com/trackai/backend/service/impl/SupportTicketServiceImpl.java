package com.trackai.backend.service.impl;

import com.trackai.backend.config.RateLimitProperties;
import com.trackai.backend.dto.RateLimitResponse;
import com.trackai.backend.dto.support.*;
import com.trackai.backend.entity.SupportTicket;
import com.trackai.backend.entity.SupportTicketMessage;
import com.trackai.backend.entity.User;
import com.trackai.backend.enums.Role;
import com.trackai.backend.enums.SupportTicketPriority;
import com.trackai.backend.enums.SupportTicketStatus;
import com.trackai.backend.repository.PaymentTransactionRepository;
import com.trackai.backend.repository.SupportTicketMessageRepository;
import com.trackai.backend.repository.SupportTicketRepository;
import com.trackai.backend.repository.UserRepository;
import com.trackai.backend.security.JwtUserPrincipal;
import com.trackai.backend.service.RedisRateLimitService;
import com.trackai.backend.service.SupportTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SupportTicketServiceImpl implements SupportTicketService {

    private final SupportTicketRepository ticketRepository;
    private final SupportTicketMessageRepository messageRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final UserRepository userRepository;
    private final RedisRateLimitService redisRateLimitService;
    private final RateLimitProperties rateLimitProperties;

    @Override
    @Transactional
    public SupportTicketResponse create(CreateSupportTicketRequest request) {
        UserContext user = currentUser();
        enforceRateLimit("create", user.id());

        String orderId = clean(request.orderId());
        if (orderId != null && paymentTransactionRepository
                .findByOrderIdAndUserId(orderId, user.id()).isEmpty()) {
            throw new RuntimeException("Payment order was not found in your account");
        }

        LocalDateTime now = LocalDateTime.now();
        SupportTicket ticket = SupportTicket.builder()
                .id(UUID.randomUUID().toString())
                .userId(user.id())
                .subject(request.subject().trim())
                .category(request.category())
                .priority(request.priority() == null ? SupportTicketPriority.NORMAL : request.priority())
                .status(SupportTicketStatus.OPEN)
                .orderId(orderId)
                .createdAt(now)
                .updatedAt(now)
                .build();
        ticketRepository.save(ticket);
        saveMessage(ticket.getId(), user, request.message());
        return detail(ticket);
    }

    @Override
    public List<SupportTicketSummaryResponse> getMine() {
        UserContext current = currentUser();
        User account = userRepository.findById(current.id()).orElse(null);
        return ticketRepository.findTop100ByUserIdOrderByUpdatedAtDesc(current.id())
                .stream().map(ticket -> summary(ticket, account)).toList();
    }

    @Override
    public SupportTicketResponse getMine(String ticketId) {
        UserContext user = currentUser();
        return detail(owned(ticketId, user.id()));
    }

    @Override
    @Transactional
    public SupportTicketResponse replyAsUser(String ticketId, SupportReplyRequest request) {
        UserContext user = currentUser();
        enforceRateLimit("reply", user.id());
        SupportTicket ticket = owned(ticketId, user.id());
        if (ticket.getStatus() == SupportTicketStatus.CLOSED) {
            throw new RuntimeException("Reopen this ticket before replying");
        }
        saveMessage(ticketId, user, request.message());
        if (ticket.getStatus() == SupportTicketStatus.WAITING_FOR_USER) {
            ticket.setStatus(SupportTicketStatus.OPEN);
        }
        touch(ticket);
        return detail(ticket);
    }

    @Override
    @Transactional
    public SupportTicketResponse resolveMine(String ticketId) {
        UserContext user = currentUser();
        SupportTicket ticket = owned(ticketId, user.id());
        ticket.setStatus(SupportTicketStatus.RESOLVED);
        ticket.setResolvedAt(LocalDateTime.now());
        touch(ticket);
        return detail(ticket);
    }

    @Override
    @Transactional
    public SupportTicketResponse reopenMine(String ticketId) {
        UserContext user = currentUser();
        SupportTicket ticket = owned(ticketId, user.id());
        ticket.setStatus(SupportTicketStatus.OPEN);
        ticket.setResolvedAt(null);
        touch(ticket);
        return detail(ticket);
    }

    @Override
    public List<SupportTicketSummaryResponse> getAll(SupportTicketStatus status) {
        List<SupportTicket> tickets = status == null
                ? ticketRepository.findTop100ByOrderByUpdatedAtDesc()
                : ticketRepository.findTop100ByStatusOrderByUpdatedAtDesc(status);
        Map<String, User> users = userRepository.findAllById(
                        tickets.stream().map(SupportTicket::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return tickets.stream().map(ticket -> summary(ticket, users.get(ticket.getUserId()))).toList();
    }

    @Override
    public SupportTicketResponse getAsAdmin(String ticketId) {
        return detail(required(ticketId));
    }

    @Override
    @Transactional
    public SupportTicketResponse replyAsAdmin(String ticketId, SupportReplyRequest request) {
        UserContext admin = currentUser();
        enforceRateLimit("admin-reply", admin.id());
        SupportTicket ticket = required(ticketId);
        saveMessage(ticketId, admin, request.message());
        ticket.setStatus(SupportTicketStatus.WAITING_FOR_USER);
        touch(ticket);
        return detail(ticket);
    }

    @Override
    @Transactional
    public SupportTicketResponse updateStatusAsAdmin(String ticketId, SupportStatusRequest request) {
        SupportTicket ticket = required(ticketId);
        ticket.setStatus(request.status());
        ticket.setResolvedAt(
                request.status() == SupportTicketStatus.RESOLVED
                        || request.status() == SupportTicketStatus.CLOSED
                        ? LocalDateTime.now() : null);
        touch(ticket);
        return detail(ticket);
    }

    @Override
    @Transactional
    public void deleteAsAdmin(String ticketId) {
        SupportTicket ticket = required(ticketId);
        messageRepository.deleteByTicketId(ticket.getId());
        ticketRepository.delete(ticket);
    }

    private void saveMessage(String ticketId, UserContext sender, String rawMessage) {
        messageRepository.save(SupportTicketMessage.builder()
                .id(UUID.randomUUID().toString())
                .ticketId(ticketId)
                .senderId(sender.id())
                .senderRole(sender.role())
                .message(rawMessage.trim())
                .createdAt(LocalDateTime.now())
                .build());
    }

    private void touch(SupportTicket ticket) {
        ticket.setUpdatedAt(LocalDateTime.now());
        ticketRepository.save(ticket);
    }

    private SupportTicket owned(String id, String userId) {
        return ticketRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RuntimeException("Support ticket not found"));
    }

    private SupportTicket required(String id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Support ticket not found"));
    }

    private SupportTicketResponse detail(SupportTicket ticket) {
        List<SupportMessageResponse> messages = messageRepository
                .findByTicketIdOrderByCreatedAtAsc(ticket.getId())
                .stream()
                .map(message -> new SupportMessageResponse(
                        message.getId(), message.getSenderId(), message.getSenderRole(),
                        message.getMessage(), message.getCreatedAt()))
                .toList();
        User account = userRepository.findById(ticket.getUserId()).orElse(null);
        return new SupportTicketResponse(summary(ticket, account), messages);
    }

    private SupportTicketSummaryResponse summary(SupportTicket ticket, User account) {
        return new SupportTicketSummaryResponse(
                ticket.getId(), ticket.getUserId(),
                account == null ? "Deleted user" : account.getName(),
                account == null ? null : account.getEmail(),
                ticket.getSubject(), ticket.getCategory(), ticket.getPriority(), ticket.getStatus(),
                ticket.getOrderId(), ticket.getCreatedAt(), ticket.getUpdatedAt(),
                ticket.getResolvedAt());
    }

    private UserContext currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof JwtUserPrincipal principal
                && principal.userId() != null && !principal.userId().isBlank()) {
            return new UserContext(
                    principal.userId(), Role.valueOf(principal.role()));
        }
        var user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return new UserContext(user.getId(), user.getRole());
    }

    private void enforceRateLimit(String action, String userId) {
        var limit = rateLimitProperties.getSupport();
        RateLimitResponse response = redisRateLimitService.allowRequest(
                "support:" + action + ":" + userId,
                limit.getCapacity(), limit.getRefillTokens(), limit.getRefillMinutes());
        if (!response.isAllowed()) {
            throw new RuntimeException(response.getMessage());
        }
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private record UserContext(String id, Role role) {
    }
}
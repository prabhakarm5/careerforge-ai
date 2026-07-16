package com.trackai.backend.service.impl;

import com.trackai.backend.config.RateLimitProperties;
import com.trackai.backend.dto.RateLimitResponse;
import com.trackai.backend.dto.admin.PromoCodeRequest;
import com.trackai.backend.dto.admin.PromoCodeResponse;
import com.trackai.backend.dto.promo.PromoClaimResponse;
import com.trackai.backend.entity.PromoClaim;
import com.trackai.backend.entity.PromoCode;
import com.trackai.backend.entity.SubscriptionPlan;
import com.trackai.backend.entity.User;
import com.trackai.backend.enums.FeatureType;
import com.trackai.backend.enums.PaymentStatus;
import com.trackai.backend.enums.PromoAudience;
import com.trackai.backend.enums.PromoClaimStatus;
import com.trackai.backend.enums.PromoRewardType;
import com.trackai.backend.exception.PromoCodeException;
import com.trackai.backend.repository.PaymentTransactionRepository;
import com.trackai.backend.repository.PromoClaimRepository;
import com.trackai.backend.repository.PromoCodeRepository;
import com.trackai.backend.repository.SubscriptionPlanRepository;
import com.trackai.backend.repository.UserRepository;
import com.trackai.backend.security.JwtUserPrincipal;
import com.trackai.backend.service.PromoCodeService;
import com.trackai.backend.service.PromoCodeService.PromoApplication;
import com.trackai.backend.service.RedisRateLimitService;
import com.trackai.backend.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PromoCodeServiceImpl implements PromoCodeService {
    private static final int RESERVATION_MINUTES = 30;

    private final PromoCodeRepository repository;
    private final PromoClaimRepository claimRepository;
    private final PaymentTransactionRepository paymentRepository;
    private final SubscriptionPlanRepository planRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final RedisRateLimitService rateLimitService;
    private final RateLimitProperties rateLimits;

    @Transactional(readOnly = true)
    public List<PromoCodeResponse> getAll() {
        limit("promo-admin-read:" + actorKey(), rateLimits.getPromoRead());
        Map<String, Long> counts = claimCounts();
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::defaults)
                .map(promo -> PromoCodeResponse.admin(promo, counts.getOrDefault(promo.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PromoCodeResponse> getAvailable() {
        User user = authenticatedUser();
        limit("promo-read:" + user.getId(), rateLimits.getPromoRead());
        Map<String, PromoClaim> claims = claimRepository.findByUserId(user.getId()).stream()
                .collect(Collectors.toMap(PromoClaim::getPromoCodeId, Function.identity()));
        Map<String, Long> counts = claimCounts();
        boolean hasRecharged = paymentRepository.existsByUserIdAndStatus(user.getId(), PaymentStatus.SUCCESS);

        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::defaults)
                .map(promo -> userResponse(promo, user, claims.get(promo.getId()), counts.getOrDefault(promo.getId(), 0L), hasRecharged))
                .filter(PromoCodeResponse::currentlyAvailable)
                .filter(item -> item.eligible() || item.claimStatus() != null)
                .toList();
    }

    @Transactional
    public PromoClaimResponse claim(String rawCode) {
        User user = authenticatedUser();
        limit("promo-claim:" + user.getId(), rateLimits.getPromoClaim());

        PromoCode promo = defaults(repository.findByCodeForUpdate(normalize(rawCode))
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Promo code was not found")));
        long totalClaims = claimRepository.countByPromoCodeId(promo.getId());
        validateCampaign(promo, totalClaims);
        Eligibility eligibility = eligibility(promo, user);
        if (!eligibility.allowed()) throw error(HttpStatus.FORBIDDEN, eligibility.message());

        PromoClaim existing = claimRepository.findForUpdate(promo.getId(), user.getId()).orElse(null);
        if (existing != null) {
            String message = existing.getStatus() == PromoClaimStatus.REDEEMED
                    ? "This reward has already been used"
                    : "Promo is already claimed and ready to use";
            return new PromoClaimResponse(message, existing.getStatus() == PromoClaimStatus.REDEEMED,
                    userResponse(promo, user, existing, totalClaims));
        }

        PromoClaim claim = PromoClaim.builder()
                .id(UUID.randomUUID().toString())
                .promoCodeId(promo.getId())
                .userId(user.getId())
                .status(PromoClaimStatus.CLAIMED)
                .claimedAt(LocalDateTime.now())
                .build();
        try {
            claimRepository.saveAndFlush(claim);
        } catch (DataIntegrityViolationException duplicate) {
            throw error(HttpStatus.CONFLICT, "This promo has already been claimed");
        }

        boolean granted = grantImmediateReward(promo, user, claim);
        String message = granted ? immediateMessage(promo) : "Promo claimed. Select a plan to apply it.";
        return new PromoClaimResponse(message, granted, userResponse(promo, user, claim, totalClaims + 1));
    }

    @Transactional
    public PromoApplication reserveForOrder(String userId, String rawCode, String orderReference) {
        if (rawCode == null || rawCode.isBlank()) return PromoApplication.none();

        PromoCode promo = defaults(repository.findByCodeForUpdate(normalize(rawCode))
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Promo code was not found")));
        if (promo.getRewardType() != PromoRewardType.DISCOUNT) {
            throw error(HttpStatus.BAD_REQUEST, "This reward is claimed directly and cannot be applied to payment");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "User not found"));
        validateCampaign(promo, claimRepository.countByPromoCodeId(promo.getId()));
        Eligibility eligibility = eligibility(promo, user);
        if (!eligibility.allowed()) throw error(HttpStatus.FORBIDDEN, eligibility.message());

        PromoClaim claim = claimRepository.findForUpdate(promo.getId(), userId)
                .orElseThrow(() -> error(HttpStatus.BAD_REQUEST, "Claim this promo before selecting a plan"));
        if (claim.getStatus() == PromoClaimStatus.REDEEMED) {
            throw error(HttpStatus.CONFLICT, "This promo has already been used");
        }
        if (claim.getStatus() == PromoClaimStatus.RESERVED && !reservationExpired(claim)) {
            throw error(HttpStatus.CONFLICT, "This promo is already attached to another payment");
        }

        claim.setStatus(PromoClaimStatus.RESERVED);
        claim.setReservedOrderId(orderReference);
        claim.setReservedAt(LocalDateTime.now());
        claimRepository.save(claim);
        return new PromoApplication(claim.getId(), promo.getCode(), promo.getDiscountPercent(), promo.getBonusTokens());
    }

    @Transactional
    public void attachOrder(String claimId, String temporaryReference, String orderId) {
        if (claimId == null) return;
        PromoClaim claim = claimRepository.findByIdForUpdate(claimId).orElse(null);
        if (claim != null && Objects.equals(claim.getReservedOrderId(), temporaryReference)) {
            claim.setReservedOrderId(orderId);
            claimRepository.save(claim);
        }
    }

    @Transactional
    public void releaseReservation(String claimId, String orderId) {
        if (claimId == null) return;
        PromoClaim claim = claimRepository.findByIdForUpdate(claimId).orElse(null);
        if (claim != null && claim.getStatus() == PromoClaimStatus.RESERVED
                && (orderId == null || Objects.equals(orderId, claim.getReservedOrderId()))) {
            claim.setStatus(PromoClaimStatus.CLAIMED);
            claim.setReservedOrderId(null);
            claim.setReservedAt(null);
            claimRepository.save(claim);
        }
    }

    @Transactional
    public void redeemPaymentClaim(String claimId, String orderId, String userId) {
        if (claimId == null) return;
        PromoClaim claim = claimRepository.findByIdForUpdate(claimId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Promo claim was not found"));
        if (claim.getStatus() == PromoClaimStatus.REDEEMED) return;
        if (!Objects.equals(claim.getUserId(), userId) || !Objects.equals(claim.getReservedOrderId(), orderId)) {
            throw error(HttpStatus.CONFLICT, "Promo claim does not match this payment");
        }
        PromoCode promo = defaults(repository.findById(claim.getPromoCodeId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Promo code was not found")));
        if (promo.getBonusTokens() > 0) {
            walletService.addTokens(userId, promo.getBonusTokens(), FeatureType.SUBSCRIPTION,
                    promo.getCode() + " promo bonus");
        }
        claim.setStatus(PromoClaimStatus.REDEEMED);
        claim.setRedeemedAt(LocalDateTime.now());
        claimRepository.save(claim);
    }

    @Transactional
    public PromoCodeResponse create(PromoCodeRequest request) {
        limit("promo-admin-write:" + actorKey(), rateLimits.getPromoAdmin());
        validateRequest(request);
        String code = normalize(request.code());
        repository.findByCodeIgnoreCase(code).ifPresent(existing -> {
            throw error(HttpStatus.CONFLICT, "Promo code already exists");
        });
        PromoCode promo = apply(PromoCode.builder().id(UUID.randomUUID().toString())
                .createdAt(LocalDateTime.now()).build(), request, code);
        return PromoCodeResponse.admin(repository.save(promo), 0);
    }

    @Transactional
    public PromoCodeResponse update(String id, PromoCodeRequest request) {
        limit("promo-admin-write:" + actorKey(), rateLimits.getPromoAdmin());
        validateRequest(request);
        PromoCode promo = repository.findById(id)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Promo code not found"));
        String code = normalize(request.code());
        repository.findByCodeIgnoreCase(code).filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> { throw error(HttpStatus.CONFLICT, "Promo code already exists"); });
        return PromoCodeResponse.admin(repository.save(apply(promo, request, code)),
                claimRepository.countByPromoCodeId(id));
    }

    @Transactional
    public void delete(String id) {
        limit("promo-admin-write:" + actorKey(), rateLimits.getPromoAdmin());
        PromoCode promo = repository.findById(id)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Promo code not found"));
        if (claimRepository.countByPromoCodeId(id) > 0) {
            // Claimed campaigns remain as an audit record, but disappear from user eligibility immediately.
            promo.setActive(false);
            repository.save(promo);
            return;
        }
        repository.deleteById(id);
    }

    private PromoCode apply(PromoCode promo, PromoCodeRequest request, String code) {
        promo.setCode(code);
        promo.setTitle(request.title().trim());
        promo.setDescription(clean(request.description()));
        promo.setDiscountPercent(request.discountPercent());
        promo.setBonusTokens(request.bonusTokens());
        promo.setRewardType(request.rewardType());
        promo.setAudience(request.audience());
        promo.setRewardPlanId(clean(request.rewardPlanId()));
        promo.setMaxTotalClaims(request.maxTotalClaims());
        promo.setTargetUserEmails(normalizeEmails(request.targetUserEmails()));
        promo.setActive(request.active());
        promo.setValidFrom(request.validFrom());
        promo.setExpiresAt(request.expiresAt());
        return promo;
    }

    private boolean grantImmediateReward(PromoCode promo, User user, PromoClaim claim) {
        if (promo.getRewardType() == PromoRewardType.DISCOUNT) return false;
        if (promo.getRewardType() == PromoRewardType.FREE_PLAN) {
            SubscriptionPlan plan = planRepository.findById(promo.getRewardPlanId())
                    .orElseThrow(() -> error(HttpStatus.BAD_REQUEST, "Reward plan no longer exists"));
            if (Boolean.FALSE.equals(plan.getActive())) throw error(HttpStatus.BAD_REQUEST, "Reward plan is inactive");
            walletService.applyPlanToWallet(user.getId(), plan);
        } else {
            walletService.addTokens(user.getId(), promo.getBonusTokens(), FeatureType.SUBSCRIPTION,
                    promo.getCode() + " reward claimed");
        }
        claim.setStatus(PromoClaimStatus.REDEEMED);
        claim.setRedeemedAt(LocalDateTime.now());
        claimRepository.save(claim);
        return true;
    }

    private PromoCodeResponse userResponse(PromoCode promo, User user, PromoClaim claim, long totalClaims) {
        return userResponse(promo, user, claim, totalClaims,
                paymentRepository.existsByUserIdAndStatus(user.getId(), PaymentStatus.SUCCESS));
    }

    private PromoCodeResponse userResponse(PromoCode promo, User user, PromoClaim claim, long totalClaims, boolean hasRecharged) {
        Eligibility eligibility = eligibility(promo, user, hasRecharged);
        return PromoCodeResponse.forUser(promo, totalClaims, eligibility.allowed(), eligibility.message(),
                claim == null ? null : claim.getStatus().name());
    }

    private Eligibility eligibility(PromoCode promo, User user) {
        return eligibility(promo, user,
                paymentRepository.existsByUserIdAndStatus(user.getId(), PaymentStatus.SUCCESS));
    }

    private Eligibility eligibility(PromoCode promo, User user, boolean hasRecharged) {
        if (promo.getAudience() == PromoAudience.NEVER_RECHARGED && hasRecharged) {
            return new Eligibility(false, "Only users who have never recharged can claim this offer");
        }
        if (promo.getAudience() == PromoAudience.SPECIFIC_USERS
                && !promo.getTargetUserEmails().contains(user.getEmail().trim().toLowerCase(Locale.ROOT))) {
            return new Eligibility(false, "This reward is assigned to selected users");
        }
        return new Eligibility(true, "Eligible to claim");
    }
    private void validateCampaign(PromoCode promo, long totalClaims) {
        LocalDateTime now = LocalDateTime.now();
        if (!Boolean.TRUE.equals(promo.getActive())) throw error(HttpStatus.BAD_REQUEST, "Promo is disabled");
        if (promo.getValidFrom() != null && promo.getValidFrom().isAfter(now)) throw error(HttpStatus.BAD_REQUEST, "Promo has not started yet");
        if (promo.getExpiresAt() != null && !promo.getExpiresAt().isAfter(now)) throw error(HttpStatus.BAD_REQUEST, "Promo has expired");
        if (promo.getMaxTotalClaims() > 0 && totalClaims >= promo.getMaxTotalClaims()) throw error(HttpStatus.CONFLICT, "Promo claim limit has been reached");
    }

    private void validateRequest(PromoCodeRequest request) {
        if (request.validFrom() != null && request.expiresAt() != null && !request.expiresAt().isAfter(request.validFrom())) {
            throw error(HttpStatus.BAD_REQUEST, "Promo expiry must be after its start time");
        }
        if (request.rewardType() == PromoRewardType.DISCOUNT && request.discountPercent() <= 0) {
            throw error(HttpStatus.BAD_REQUEST, "Discount promos need a discount greater than zero");
        }
        if (request.rewardType() == PromoRewardType.BONUS_TOKENS && request.bonusTokens() <= 0) {
            throw error(HttpStatus.BAD_REQUEST, "Token rewards need bonus tokens greater than zero");
        }
        if (request.rewardType() == PromoRewardType.FREE_PLAN
                && (request.rewardPlanId() == null || request.rewardPlanId().isBlank())) {
            throw error(HttpStatus.BAD_REQUEST, "Select a plan for the free-plan reward");
        }
        if (request.audience() == PromoAudience.SPECIFIC_USERS && normalizeEmails(request.targetUserEmails()).isEmpty()) {
            throw error(HttpStatus.BAD_REQUEST, "Add at least one target user email");
        }
    }

    private PromoCode defaults(PromoCode promo) {
        if (promo.getRewardType() == null) promo.setRewardType(PromoRewardType.DISCOUNT);
        if (promo.getAudience() == null) promo.setAudience(PromoAudience.ALL_USERS);
        if (promo.getDiscountPercent() == null) promo.setDiscountPercent(0);
        if (promo.getBonusTokens() == null) promo.setBonusTokens(0L);
        if (promo.getMaxTotalClaims() == null) promo.setMaxTotalClaims(0);
        if (promo.getTargetUserEmails() == null) promo.setTargetUserEmails(new HashSet<>());
        return promo;
    }

    private Map<String, Long> claimCounts() {
        return claimRepository.countAllByPromoCode().stream()
                .collect(Collectors.toMap(PromoClaimRepository.PromoClaimCount::getPromoCodeId,
                        PromoClaimRepository.PromoClaimCount::getClaimCount));
    }

    private User authenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw error(HttpStatus.UNAUTHORIZED, "Please log in to use promo codes");
        }
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "User account was not found"));
    }

    private String actorKey() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof JwtUserPrincipal principal
                && principal.userId() != null && !principal.userId().isBlank()) {
            return principal.userId();
        }
        String name = authentication == null ? "anonymous" : String.valueOf(authentication.getName());
        return Integer.toUnsignedString(name.toLowerCase(Locale.ROOT).hashCode());
    }
    private void limit(String key, RateLimitProperties.Limit limit) {
        RateLimitResponse result = rateLimitService.allowRequest(
                key, limit.getCapacity(), limit.getRefillTokens(), limit.getRefillMinutes());
        if (!result.isAllowed()) throw error(HttpStatus.TOO_MANY_REQUESTS, result.getMessage());
    }

    private boolean reservationExpired(PromoClaim claim) {
        return claim.getReservedAt() == null || claim.getReservedAt().isBefore(LocalDateTime.now().minusMinutes(RESERVATION_MINUTES));
    }

    private Set<String> normalizeEmails(Set<String> values) {
        if (values == null) return new HashSet<>();
        return values.stream().filter(Objects::nonNull).map(String::trim)
                .filter(value -> !value.isBlank()).map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(HashSet::new));
    }

    private String normalize(String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_-]", "");
        if (normalized.isBlank()) throw error(HttpStatus.BAD_REQUEST, "Enter a valid promo code");
        return normalized;
    }

    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String immediateMessage(PromoCode promo) {
        return promo.getRewardType() == PromoRewardType.FREE_PLAN
                ? "Free plan activated successfully" : "Reward tokens added to your wallet";
    }
    private PromoCodeException error(HttpStatus status, String message) { return new PromoCodeException(status, message); }

    private record Eligibility(boolean allowed, String message) {}
}

package com.trackai.backend.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackai.backend.dto.cache.CachedWallet;
import com.trackai.backend.service.RedisWalletCacheService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisWalletCacheServiceImpl
        implements RedisWalletCacheService {

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    private static final String WALLET_PREFIX = "wallet_cache:";

    /*
     * TTL sirf 5 minute — kam rakha hai jaanbhoojkar. Ye ek
     * WRITE-THROUGH cache hai (har write pe refresh hota hai),
     * isliye TTL sirf ek fallback safety-net hai, normal case
     * mein kabhi expire hone se pehle hi refresh ho jaati hai.
     * Chhoti TTL ka fayda: agar kisi rare bug/edge-case mein
     * cache update miss ho jaaye, to zyada der galat balance
     * nahi dikhega — max 5 min mein khud fix ho jaayega.
     */
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    // ================= SAVE WALLET (write-through) =================
    @Override
    public void saveWallet(CachedWallet wallet) {

        if (wallet == null || wallet.getUserId() == null) {
            return;
        }

        try {

            String key = WALLET_PREFIX + wallet.getUserId();

            String json = objectMapper.writeValueAsString(wallet);

            redisTemplate.opsForValue().set(
                    key,
                    json,
                    CACHE_TTL);

            log.info("Wallet cached (write-through) : {}", key);

        } catch (JsonProcessingException e) {

            log.error("Failed to serialize wallet cache", e);

        } catch (Exception e) {

            // IMPORTANT: Redis fail hone par bhi DB write already
            // ho chuka hai (ye method DB save ke BAAD call hoti hai),
            // isliye yahan exception silently log karo — user ka
            // actual wallet operation fail nahi hona chahiye sirf
            // isliye ki cache update fail hui.
            log.error("Redis save failed (wallet) — DB write already succeeded, continuing", e);
        }
    }

    // ================= GET WALLET =================
    @Override
    public CachedWallet getWallet(String userId) {

        try {

            String key = WALLET_PREFIX + userId;

            String json = redisTemplate.opsForValue().get(key);

            if (json == null) {

                log.info("Redis Cache MISS : {}", key);

                return null;
            }

            log.info("Redis Cache HIT : {}", key);

            return objectMapper.readValue(
                    json,
                    CachedWallet.class);

        } catch (Exception e) {

            log.error("Redis read failed (wallet)", e);

            return null;
        }
    }

    // ================= EVICT WALLET =================
    @Override
    public void evictWallet(String userId) {

        try {

            String key = WALLET_PREFIX + userId;

            redisTemplate.delete(key);

        log.info("Wallet cache evicted : {}", key);

        } catch (Exception e) {

            log.error("Redis delete failed (wallet)", e);
        }
    }
}
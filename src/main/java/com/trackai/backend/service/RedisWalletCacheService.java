package com.trackai.backend.service;

import com.trackai.backend.dto.cache.CachedWallet;

public interface RedisWalletCacheService {

        // Wallet ko cache mein save/overwrite karo (WRITE-THROUGH —
        // har DB save ke turant baad ye call hogi)
        void saveWallet(CachedWallet wallet);

        // Cache se wallet nikalo (null agar cache MISS)
        CachedWallet getWallet(String userId);

        // Cache hatao (rarely needed, sirf safety/debug ke liye —
        // normal flow mein saveWallet() hi kaafi hai kyunki wo
        // overwrite kar deta hai)
        void evictWallet(String userId);
}
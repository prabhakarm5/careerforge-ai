package com.trackai.backend.service;

import com.trackai.backend.dto.cache.CachedUser;

public interface RedisUserCacheService {

    /*
     * ============================================================
     * Save User in Redis
     * ============================================================
     */
    void saveUser(CachedUser user);

    /*
     * ============================================================
     * Get User from Redis
     * ============================================================
     */
    CachedUser getUser(String email);

    /*
     * ============================================================
     * Delete User Cache
     * ============================================================
     */
    void deleteUser(String email);

    /*
     * ============================================================
     * Update Cache
     * ============================================================
     */
    void updateUser(CachedUser user);

}
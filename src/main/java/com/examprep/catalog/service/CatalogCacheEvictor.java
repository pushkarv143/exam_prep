package com.examprep.catalog.service;

import com.examprep.common.cache.CacheNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Clears the catalog caches <b>after the surrounding transaction commits</b>.
 * {@code @CacheEvict} runs before commit, which leaves a window: a concurrent reader
 * could re-cache the old tree before the new rows become visible, and that stale entry
 * would then live for the full TTL.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class CatalogCacheEvictor {

    private final CacheManager cacheManager;

    void evictAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictNow();
                }
            });
        } else {
            evictNow();
        }
    }

    private void evictNow() {
        for (String name : new String[]{CacheNames.CATALOG_EXAMS, CacheNames.CATALOG_TREE}) {
            try {
                Cache cache = cacheManager.getCache(name);
                if (cache != null) {
                    cache.clear();
                }
            } catch (RuntimeException e) {
                // TTL (6h) bounds staleness if Redis is briefly unavailable.
                log.warn("Failed to evict cache {}: {}", name, e.getMessage());
            }
        }
    }
}

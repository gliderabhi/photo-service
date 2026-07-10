package com.sevis.photoservice.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

// In-process (Caffeine) caching — this service runs as a single instance with
// no shared/replicated state, so there's no need for a centralized cache here;
// each cache below is evicted explicitly by the write paths that invalidate it
// (see @CacheEvict usages), with a short TTL as a backstop only.
//
// Only pure metadata reads are cached here (album/photo listings, folder
// existence). Auth/ownership checks (folder password verification) and any
// endpoint that streams raw photo bytes are never cached — see FolderService
// and PhotoController/PhotoService for details.
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                buildCache("folderStatus", 5, TimeUnit.MINUTES, 1000),
                buildCache("albumsByUser", 1, TimeUnit.MINUTES, 1000),
                buildCache("albumPhotos", 1, TimeUnit.MINUTES, 2000),
                buildCache("photosByDate", 30, TimeUnit.SECONDS, 1000)
        ));
        return manager;
    }

    private CaffeineCache buildCache(String name, long ttl, TimeUnit unit, int maxSize) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .expireAfterWrite(ttl, unit)
                .maximumSize(maxSize)
                .recordStats()
                .build());
    }
}

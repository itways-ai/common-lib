package com.itways.cache.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import com.github.benmanes.caffeine.cache.Policy;

/** PLT-22: the default CacheManager is bounded in size and time. */
class DefaultCacheManagerTest {

	@Test
	void defaultsAreTenThousandEntriesAndTenMinutes() {
		CacheProperties properties = new CacheProperties();

		assertThat(properties.getManager().getMaximumSize()).isEqualTo(10_000);
		assertThat(properties.getManager().getTtl()).isEqualTo(Duration.ofMinutes(10));

		com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache = nativeCache(manager(properties), "any");
		Policy.Eviction<Object, Object> eviction = nativeCache.policy().eviction().orElseThrow();
		assertThat(eviction.getMaximum()).isEqualTo(10_000);
		assertThat(nativeCache.policy().expireAfterWrite().orElseThrow().getExpiresAfter(TimeUnit.MINUTES))
				.isEqualTo(10);
	}

	@Test
	void everyCacheIsCappedAtTheConfiguredSize() {
		CacheProperties properties = bind(Map.of("itways.cache.manager.maximum-size", "5",
				"itways.cache.manager.ttl", "90s"));
		CacheManager manager = manager(properties);

		Cache cache = manager.getCache("lookups");
		for (int i = 0; i < 500; i++) {
			cache.put(i, "v" + i);
		}
		com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache = nativeCache(manager, "lookups");
		nativeCache.cleanUp();

		assertThat(nativeCache.estimatedSize()).isLessThanOrEqualTo(5);
		assertThat(nativeCache.policy().expireAfterWrite().orElseThrow().getExpiresAfter(TimeUnit.SECONDS))
				.isEqualTo(90);
		// Caches are still made on first use, by any name, and may hold null.
		manager.getCache("another").put("k", null);
		assertThat(manager.getCache("another").get("k")).isNotNull();
	}

	private static CacheManager manager(CacheProperties properties) {
		CacheManager manager = new CacheProviderConfig().cacheManager(properties);
		assertThat(manager).isInstanceOf(CaffeineCacheManager.class);
		return manager;
	}

	@SuppressWarnings("unchecked")
	private static com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache(CacheManager manager,
			String name) {
		return (com.github.benmanes.caffeine.cache.Cache<Object, Object>) manager.getCache(name).getNativeCache();
	}

	private static CacheProperties bind(Map<String, String> values) {
		return new Binder(new MapConfigurationPropertySource(values)).bind("itways.cache", CacheProperties.class)
				.orElseThrow(IllegalStateException::new);
	}
}

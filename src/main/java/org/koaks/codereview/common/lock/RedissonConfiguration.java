package org.koaks.codereview.common.lock;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/** Creates a Redisson client from the application's existing Redis connection settings. */
@Configuration(proxyBeanMethods = false)
public class RedissonConfiguration {

    @Bean(destroyMethod = "shutdown")
    RedissonClient redissonClient(DataRedisConnectionDetails connectionDetails) {
        DataRedisConnectionDetails.Standalone standalone = connectionDetails.getStandalone();
        if (standalone == null) {
            throw new IllegalStateException("RedissonLock currently supports a standalone Redis server only");
        }

        Config config = new Config();
        config.setLazyInitialization(true);
        var server = config.useSingleServer()
                .setAddress("redis://" + standalone.getHost() + ":" + standalone.getPort())
                .setDatabase(standalone.getDatabase());
        if (StringUtils.hasText(connectionDetails.getUsername())) {
            server.setUsername(connectionDetails.getUsername());
        }
        if (StringUtils.hasText(connectionDetails.getPassword())) {
            server.setPassword(connectionDetails.getPassword());
        }
        return Redisson.create(config);
    }

}

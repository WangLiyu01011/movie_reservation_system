package me.wly.movie_reservation.common.util;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

public final class RedisScriptLoader {

    private RedisScriptLoader() {
    }

    public static <T> RedisScript<T> script(
            String resource,
            Class<T> resultType
    ) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(resource));
        script.setResultType(resultType);
        return script;
    }
}

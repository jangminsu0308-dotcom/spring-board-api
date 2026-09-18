package com.example.demo.common;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterServiceTest {

    @Test
    void 최대_횟수_미만이면_허용된다() {
        RateLimiterService service = new RateLimiterService();

        service.checkAllowed("post:writer", 3, Duration.ofMinutes(1));
        service.checkAllowed("post:writer", 3, Duration.ofMinutes(1));

        assertThatCode(() -> service.checkAllowed("post:writer", 3, Duration.ofMinutes(1)))
                .doesNotThrowAnyException();
    }

    @Test
    void 최대_횟수에_도달하면_예외를_던진다() {
        RateLimiterService service = new RateLimiterService();

        service.checkAllowed("post:writer", 3, Duration.ofMinutes(1));
        service.checkAllowed("post:writer", 3, Duration.ofMinutes(1));
        service.checkAllowed("post:writer", 3, Duration.ofMinutes(1));

        assertThatThrownBy(() -> service.checkAllowed("post:writer", 3, Duration.ofMinutes(1)))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void 시간이_지나면_다시_허용된다() throws InterruptedException {
        RateLimiterService service = new RateLimiterService();
        Duration window = Duration.ofMillis(30);

        service.checkAllowed("post:writer", 1, window);
        assertThatThrownBy(() -> service.checkAllowed("post:writer", 1, window))
                .isInstanceOf(TooManyRequestsException.class);

        Thread.sleep(50);

        assertThatCode(() -> service.checkAllowed("post:writer", 1, window)).doesNotThrowAnyException();
    }

    @Test
    void 다른_키는_서로_영향을_주지_않는다() {
        RateLimiterService service = new RateLimiterService();

        service.checkAllowed("post:writer", 1, Duration.ofMinutes(1));

        assertThatCode(() -> service.checkAllowed("comment:writer", 1, Duration.ofMinutes(1)))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.checkAllowed("post:other", 1, Duration.ofMinutes(1)))
                .doesNotThrowAnyException();
    }
}

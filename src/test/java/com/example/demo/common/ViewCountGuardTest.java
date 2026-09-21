package com.example.demo.common;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ViewCountGuardTest {

    @Test
    void 처음_보면_카운트한다() {
        ViewCountGuard guard = new ViewCountGuard(Duration.ofMinutes(5));

        assertThat(guard.shouldCount(1L, "writer")).isTrue();
    }

    @Test
    void 같은_사람이_짧은_시간_안에_다시_보면_카운트하지_않는다() {
        ViewCountGuard guard = new ViewCountGuard(Duration.ofMinutes(5));

        guard.shouldCount(1L, "writer");

        assertThat(guard.shouldCount(1L, "writer")).isFalse();
    }

    @Test
    void 시간이_지나면_다시_카운트한다() throws InterruptedException {
        ViewCountGuard guard = new ViewCountGuard(Duration.ofMillis(30));

        guard.shouldCount(1L, "writer");
        Thread.sleep(50);

        assertThat(guard.shouldCount(1L, "writer")).isTrue();
    }

    @Test
    void 다른_게시글이나_다른_조회자는_서로_영향을_주지_않는다() {
        ViewCountGuard guard = new ViewCountGuard(Duration.ofMinutes(5));

        guard.shouldCount(1L, "writer");

        assertThat(guard.shouldCount(2L, "writer")).isTrue();
        assertThat(guard.shouldCount(1L, "other")).isTrue();
    }
}

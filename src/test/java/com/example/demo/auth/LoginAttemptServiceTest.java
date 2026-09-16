package com.example.demo.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptServiceTest {

    @Test
    void 최대_시도_횟수_미만이면_잠기지_않는다() {
        LoginAttemptService service = new LoginAttemptService(3, 60_000);

        service.recordFailure("writer");
        service.recordFailure("writer");

        assertThatCode(() -> service.checkNotLocked("writer")).doesNotThrowAnyException();
    }

    @Test
    void 최대_시도_횟수에_도달하면_잠긴다() {
        LoginAttemptService service = new LoginAttemptService(3, 60_000);

        service.recordFailure("writer");
        service.recordFailure("writer");
        service.recordFailure("writer");

        assertThatThrownBy(() -> service.checkNotLocked("writer"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    void 로그인에_성공하면_실패_기록이_초기화된다() {
        LoginAttemptService service = new LoginAttemptService(3, 60_000);

        service.recordFailure("writer");
        service.recordFailure("writer");
        service.recordSuccess("writer");
        service.recordFailure("writer");

        // 성공으로 카운트가 리셋됐으니, 그 후 실패 1번으로는 아직 잠기지 않아야 한다
        assertThatCode(() -> service.checkNotLocked("writer")).doesNotThrowAnyException();
    }

    @Test
    void 잠금_시간이_지나면_자동으로_풀린다() throws InterruptedException {
        LoginAttemptService service = new LoginAttemptService(2, 30); // 30ms만 잠금

        service.recordFailure("writer");
        service.recordFailure("writer");
        assertThatThrownBy(() -> service.checkNotLocked("writer"))
                .isInstanceOf(TooManyLoginAttemptsException.class);

        Thread.sleep(50);

        assertThatCode(() -> service.checkNotLocked("writer")).doesNotThrowAnyException();
    }

    @Test
    void 다른_아이디의_실패는_서로_영향을_주지_않는다() {
        LoginAttemptService service = new LoginAttemptService(2, 60_000);

        service.recordFailure("writer");
        service.recordFailure("writer");

        assertThatCode(() -> service.checkNotLocked("other")).doesNotThrowAnyException();
    }
}

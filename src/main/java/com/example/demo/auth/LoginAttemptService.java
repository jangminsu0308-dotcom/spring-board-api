package com.example.demo.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 로그인 실패를 아이디 기준으로 세어, 짧은 시간에 너무 많이 틀리면 잠시 잠근다(브루트포스 방어).
 * 서버 프로세스 메모리에만 저장하는 카운터라 서버를 여러 대로 늘리면 계정당 허용 시도 횟수가
 * 서버 대수만큼 늘어나는 한계가 있다 — 그 규모가 되면 Redis 등 공유 저장소로 옮겨야 한다.
 */
@Component
public class LoginAttemptService {

    private final int maxAttempts;
    private final Duration lockoutDuration;
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public LoginAttemptService(
            @Value("${login.max-attempts:5}") int maxAttempts,
            @Value("${login.lockout-duration-ms:300000}") long lockoutDurationMs) {
        this.maxAttempts = maxAttempts;
        this.lockoutDuration = Duration.ofMillis(lockoutDurationMs);
    }

    /** 잠긴 상태면 예외를 던진다. 잠금 시간이 지났으면 기록을 지우고 통과시킨다. */
    public void checkNotLocked(String username) {
        Attempt attempt = attempts.get(username);
        if (attempt == null) {
            return;
        }
        synchronized (attempt) {
            if (attempt.lockedUntil == null) {
                return;
            }
            if (!Instant.now().isBefore(attempt.lockedUntil)) {
                attempts.remove(username);
                return;
            }
            long remainingSeconds = Duration.between(Instant.now(), attempt.lockedUntil).getSeconds() + 1;
            throw new TooManyLoginAttemptsException(remainingSeconds);
        }
    }

    public void recordFailure(String username) {
        Attempt attempt = attempts.computeIfAbsent(username, k -> new Attempt());
        synchronized (attempt) {
            attempt.count++;
            if (attempt.count >= maxAttempts) {
                attempt.lockedUntil = Instant.now().plus(lockoutDuration);
            }
        }
    }

    public void recordSuccess(String username) {
        attempts.remove(username);
    }

    private static class Attempt {
        int count;
        Instant lockedUntil;
    }
}

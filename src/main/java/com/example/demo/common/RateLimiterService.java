package com.example.demo.common;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 슬라이딩 윈도우 방식의 단순한 요청 횟수 제한기.
 * auth.LoginAttemptService는 "실패한 시도"만 세어 잠그지만, 이건 성공 여부와 무관하게
 * 일정 시간 안에 몇 번 했는지를 센다 — 게시글·댓글 도배(스팸) 방지용이다.
 * 서버 프로세스 메모리에만 저장하므로, 서버를 여러 대로 늘리면 LoginAttemptService와
 * 같은 한계(계정당 허용 횟수가 서버 대수만큼 늘어남)를 그대로 가진다 — 그 규모가 되면
 * Redis 같은 공유 저장소로 옮겨야 한다.
 */
@Component
public class RateLimiterService {

    private final Map<String, Deque<Instant>> log = new ConcurrentHashMap<>();

    public void checkAllowed(String key, int maxRequests, Duration window) {
        Deque<Instant> timestamps = log.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        Instant now = Instant.now();
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && Duration.between(timestamps.peekFirst(), now).compareTo(window) >= 0) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxRequests) {
                long retryAfterSeconds = Duration.between(now, timestamps.peekFirst().plus(window)).getSeconds() + 1;
                throw new TooManyRequestsException(retryAfterSeconds);
            }
            timestamps.addLast(now);
        }
    }
}

package com.example.demo.common;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 짧은 시간 안에 같은 사람이 같은 글을 새로고침만 해도 조회수가 계속 올라가는 것을 막는다.
 * (게시글 id, 조회자 식별자) 조합별로 마지막으로 "실제로 센" 시각만 기억해두고, WINDOW가
 * 지나기 전에 다시 오면 세지 않는다 — WINDOW가 지나면 다시 정상적으로 센다.
 * RateLimiterService와 같은 이유로 서버 메모리에만 저장한다: 조회수는 정밀함보다 "대략의
 * 인기 지표"가 목적이라 서버 재시작·다중 서버 환경에서의 근사치 정도로도 충분하다.
 */
@Component
public class ViewCountGuard {

    private static final Duration DEFAULT_WINDOW = Duration.ofMinutes(5);

    private final Duration window;
    private final Map<String, Instant> lastCountedAt = new ConcurrentHashMap<>();

    public ViewCountGuard() {
        this(DEFAULT_WINDOW);
    }

    // 테스트에서 WINDOW를 짧게 줘서 "시간이 지나면 다시 세진다"를 실제로 기다리지 않고 검증하기 위함.
    ViewCountGuard(Duration window) {
        this.window = window;
    }

    public boolean shouldCount(Long postId, String viewerKey) {
        String key = postId + ":" + viewerKey;
        Instant now = Instant.now();
        Instant last = lastCountedAt.get(key);
        if (last != null && Duration.between(last, now).compareTo(window) < 0) {
            return false;
        }
        lastCountedAt.put(key, now);
        return true;
    }
}

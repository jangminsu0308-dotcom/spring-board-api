package com.example.demo.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * VM의 healthcheck-notify.sh는 "서버가 살아있는지"만 5분마다 확인한다(운영 챕터 참고).
 * 서버는 멀쩡히 떠 있는데 특정 요청에서 처리되지 않은 예외가 반복되는 경우는 그 감시망 밖이라,
 * GlobalExceptionHandler가 500을 만들 때 이 컴포넌트로 직접 ntfy 알림을 보낸다.
 *
 * 알림 전송이 느리거나 실패해도 사용자에게 가는 원래 500 응답을 지연시키거나 깨뜨리면 안 되므로
 * 비동기(sendAsync)로 보내고, 실패하면 로그만 남긴다.
 */
@Component
public class NtfyNotifier {

    private static final Logger log = LoggerFactory.getLogger(NtfyNotifier.class);
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final Duration ERROR_COOLDOWN = Duration.ofMinutes(2);

    private final String topic;
    private Instant lastErrorNotifiedAt = Instant.EPOCH;

    public NtfyNotifier(@Value("${ntfy.topic:}") String topic) {
        this.topic = topic;
    }

    /**
     * 같은 버그가 반복 호출되며 500을 계속 뱉으면 알림도 계속 쌓일 수 있어,
     * 마지막 알림 후 2분 동안은 추가로 보내지 않는다.
     */
    public synchronized void notifyUnexpectedError(String summary) {
        if (topic == null || topic.isBlank()) {
            return;
        }
        Instant now = Instant.now();
        if (Duration.between(lastErrorNotifiedAt, now).compareTo(ERROR_COOLDOWN) < 0) {
            return;
        }
        lastErrorNotifiedAt = now;

        // Title 헤더에 한글을 넣으면 java.net.http.HttpClient가 "invalid header value"로 예외를 던진다 —
        // HTTP 헤더 값은 원칙적으로 ASCII 범위라 curl은 관대하게 그냥 보내지만 Java는 엄격하게 막는다.
        // 그래서 헤더는 ASCII로만 쓰고, 한글이 들어가는 실제 내용은 본문(body)에 담는다.
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://ntfy.sh/" + topic))
                .header("Title", "spring-board-api error")
                .header("Priority", "high")
                .POST(HttpRequest.BodyPublishers.ofString(summary, StandardCharsets.UTF_8))
                .build();
        CLIENT.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .exceptionally(e -> {
                    log.warn("ntfy 알림 전송 실패", e);
                    return null;
                });
    }
}

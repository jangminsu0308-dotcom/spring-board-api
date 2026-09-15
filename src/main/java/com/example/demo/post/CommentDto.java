package com.example.demo.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public class CommentDto {

    public record Request(
        @NotBlank(message = "내용은 필수입니다")
        @Size(max = 1000, message = "내용은 1000자를 넘을 수 없습니다")
        String content
    ) {}

    public record UpdateRequest(
        @NotBlank(message = "내용은 필수입니다")
        @Size(max = 1000, message = "내용은 1000자를 넘을 수 없습니다")
        String content
    ) {}

    public record Response(
        Long id,
        String content,
        String author,
        LocalDateTime createdAt
    ) {
        static Response from(Comment comment) {
            return new Response(
                comment.getId(),
                comment.getContent(),
                comment.getAuthor().getUsername(),
                comment.getCreatedAt()
            );
        }
    }
}

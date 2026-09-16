package com.example.demo.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import java.time.LocalDateTime;
import java.util.List;

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

    public record PageResponse(
        List<Response> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
        static PageResponse from(Page<Comment> page) {
            return new PageResponse(
                page.getContent().stream().map(Response::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
            );
        }
    }
}

package com.example.demo.post;

import java.time.LocalDateTime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PostDto {

	public record Request(
		@NotBlank(message = "제목은 필수입니다")
		@Size(max = 200, message = "제목은 200자를 넘을 수 없습니다")
		String title,

		@NotBlank(message = "내용은 필수입니다")
		String content
	) {}

	public record Response(
		Long id,
		String title,
		String content,
		LocalDateTime createdAt,
		LocalDateTime updatedAt
	) {
	static Response from(Post post) {
		return new Response(
			post.getId(),
			post.getTitle(),
			post.getContent(),
			post.getCreatedAt(),
			post.getUpdatedAt()
			);
		}
	}
}

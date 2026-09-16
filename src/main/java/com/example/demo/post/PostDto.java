package com.example.demo.post;

import java.time.LocalDateTime;
import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;

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
		String author,
		LocalDateTime createdAt,
		LocalDateTime updatedAt
	) {
	static Response from(Post post) {
		return new Response(
			post.getId(),
			post.getTitle(),
			post.getContent(),
			post.getAuthor().getUsername(),
			post.getCreatedAt(),
			post.getUpdatedAt()
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
		static PageResponse from(Page<Post> page) {
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

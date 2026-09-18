package com.example.demo.post;

import java.time.LocalDateTime;
import java.util.List;
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
		String author,
		LocalDateTime createdAt,
		LocalDateTime updatedAt,
		long likeCount,
		boolean likedByMe,
		long commentCount
	) {
		// 좋아요·댓글 개수는 Post 엔티티만으로는 알 수 없다(별도 테이블 집계가 필요) —
		// 그래서 from(Post)이 아니라 이미 집계해온 값을 그대로 받는다.
		static Response from(Post post, long likeCount, boolean likedByMe, long commentCount) {
			return new Response(
				post.getId(),
				post.getTitle(),
				post.getContent(),
				post.getAuthor().getUsername(),
				post.getCreatedAt(),
				post.getUpdatedAt(),
				likeCount,
				likedByMe,
				commentCount
			);
		}
	}

	public record PageResponse(
		List<Response> content,
		int page,
		int size,
		long totalElements,
		int totalPages
	) {}

	public record LikeResponse(
		long likeCount,
		boolean likedByMe
	) {}
}

package com.example.demo.post;

import com.example.demo.auth.User;
import com.example.demo.auth.UserRepository;
import com.example.demo.common.RateLimiterService;
import com.example.demo.common.ViewCountGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

	private final PostRepository postRepository;
	private final UserRepository userRepository;
	private final PostLikeRepository postLikeRepository;
	private final CommentRepository commentRepository;
	private final RateLimiterService rateLimiter;
	private final ViewCountGuard viewCountGuard;

	private static final int MAX_POSTS_PER_MINUTE = 5;

	@Transactional
	public PostDto.Response create(PostDto.Request request, String username) {
		rateLimiter.checkAllowed("post:" + username, MAX_POSTS_PER_MINUTE, Duration.ofMinutes(1));
		User author = getUser(username);
		Post post = new Post(request.title(), request.content(), author);
		return toResponse(postRepository.save(post), username);
	}

	private static final int MAX_PAGE_SIZE = 100;

	public PostDto.PageResponse findAll(int page, int size, String keyword, String sort, boolean mine, String username) {
		int pageNum = Math.max(page, 0);
		int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		// 리포지토리 쿼리는 "필터가 없으면 null"을 기대한다 — 빈 문자열이 아니라 null이어야
		// ":keyword IS NULL OR ..." 패턴이 "필터 없음"으로 인식된다.
		// 검색어를 이중 인용부호로 감싸 phrase search로 보내므로(PostRepository 참고), 검색어
		// 안에 있는 "는 미리 제거한다 — 안 그러면 인용부호 개수가 안 맞아 MySQL 쪽에서 문법 오류가 난다.
		String sanitizedKeyword = keyword == null ? null : keyword.replace("\"", "").trim();
		String searchKeyword = (sanitizedKeyword == null || sanitizedKeyword.isBlank()) ? null : sanitizedKeyword;
		String authorFilter = (mine && username != null) ? username : null;

		Page<Post> result;
		if ("popular".equals(sort)) {
			// 좋아요 개수로 정렬하는 쿼리가 직접 ORDER BY를 만들므로, Pageable에는 정렬을 싣지 않는다.
			Pageable pageable = PageRequest.of(pageNum, pageSize);
			result = postRepository.searchOrderByLikeCountDesc(searchKeyword, authorFilter, pageable);
		} else {
			Pageable pageable = PageRequest.of(pageNum, pageSize, sortFor(sort));
			result = postRepository.search(searchKeyword, authorFilter, pageable);
		}

		List<Long> postIds = result.getContent().stream().map(Post::getId).toList();

		// search()/searchOrderByLikeCountDesc()가 네이티브 쿼리라 @EntityGraph로 author를 함께
		// 로딩할 수 없다(author는 지연 로딩 프록시로 남는다). 이 페이지에 나온 author id를 모아
		// 한 번에 findAllById로 조회해두면, 그 결과가 영속성 컨텍스트(세션)에 올라가면서 이후
		// post.getAuthor().getUsername() 호출이 DB를 다시 안 타고 세션에서 바로 해석된다 —
		// getAuthor().getId()는 프록시가 이미 알고 있는 값이라 이 시점에는 쿼리가 나가지 않는다.
		List<Long> authorIds = result.getContent().stream().map(post -> post.getAuthor().getId()).distinct().toList();
		userRepository.findAllById(authorIds);

		// 좋아요·댓글 개수를 게시글마다 따로 조회하면 페이지당 1+N번이 된다(6장/13장의 N+1과 같은 문제).
		// 이 페이지에 있는 post id 전체를 한 번에 묶어 쿼리 1~2번으로 끝낸다.
		Map<Long, Long> likeCounts = toCountMap(postLikeRepository.countGroupedByPostIds(postIds));
		Map<Long, Long> commentCounts = toCountMap(commentRepository.countGroupedByPostIds(postIds));
		Set<Long> likedByMePostIds = (username == null || postIds.isEmpty())
				? Set.of()
				: new HashSet<>(postLikeRepository.findLikedPostIds(username, postIds));

		List<PostDto.Response> content = result.getContent().stream()
				.map(post -> PostDto.Response.from(
						post,
						likeCounts.getOrDefault(post.getId(), 0L),
						likedByMePostIds.contains(post.getId()),
						commentCounts.getOrDefault(post.getId(), 0L)))
				.toList();

		return new PostDto.PageResponse(content, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
	}

	private Sort sortFor(String sort) {
		return switch (sort == null ? "" : sort) {
			case "oldest" -> Sort.by(Sort.Direction.ASC, "id");
			case "title" -> Sort.by(Sort.Direction.ASC, "title");
			default -> Sort.by(Sort.Direction.DESC, "id");
		};
	}

	private Map<Long, Long> toCountMap(List<Object[]> rows) {
		Map<Long, Long> map = new HashMap<>();
		for (Object[] row : rows) {
			map.put((Long) row[0], (Long) row[1]);
		}
		return map;
	}

	@Transactional
	public PostDto.Response findById(Long id, String username, String viewerKey) {
		Post post = postRepository.findById(id)
			.orElseThrow(() -> new PostNotFoundException(id));
		if (viewCountGuard.shouldCount(id, viewerKey)) {
			post.increaseViewCount();
		}
		return toResponse(post, username);
	}

	@Transactional
	public PostDto.Response update(Long id, PostDto.Request request, String username) {
		Post post = postRepository.findById(id)
			.orElseThrow(() -> new PostNotFoundException(id));
		validateOwner(post.getAuthor().getUsername(), username);
		post.update(request.title(), request.content());
		return toResponse(post, username);
	}

	@Transactional
	public void delete(Long id, String username) {
		Post post = postRepository.findById(id)
			.orElseThrow(() -> new PostNotFoundException(id));
		validateOwner(post.getAuthor().getUsername(), username);
		postRepository.delete(post);
	}

	/** 이미 좋아요를 눌렀으면 취소하고, 안 눌렀으면 좋아요를 남긴다. */
	@Transactional
	public PostDto.LikeResponse toggleLike(Long postId, String username) {
		Post post = postRepository.findById(postId)
				.orElseThrow(() -> new PostNotFoundException(postId));
		User user = getUser(username);

		boolean likedByMe;
		var existing = postLikeRepository.findByPostAndUser(post, user);
		if (existing.isPresent()) {
			postLikeRepository.delete(existing.get());
			likedByMe = false;
		} else {
			postLikeRepository.save(new PostLike(post, user));
			likedByMe = true;
		}
		return new PostDto.LikeResponse(postLikeRepository.countByPost(post), likedByMe);
	}

	private PostDto.Response toResponse(Post post, String username) {
		long likeCount = postLikeRepository.countByPost(post);
		boolean likedByMe = username != null && postLikeRepository.existsByPostAndUser_Username(post, username);
		long commentCount = commentRepository.countByPostId(post.getId());
		return PostDto.Response.from(post, likeCount, likedByMe, commentCount);
	}

	private User getUser(String username) {
		return userRepository.findByUsername(username)
			.orElseThrow(() -> new IllegalStateException("인증된 사용자를 찾을 수 없습니다: " + username));
	}

	private void validateOwner(String ownerUsername, String requestUsername) {
		if (!ownerUsername.equals(requestUsername)) {
			throw new AccessDeniedException("본인이 작성한 글만 수정/삭제할 수 있습니다");
		}
	}
}

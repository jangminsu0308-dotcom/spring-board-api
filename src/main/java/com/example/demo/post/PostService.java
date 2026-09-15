package com.example.demo.post;

import com.example.demo.auth.User;
import com.example.demo.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

	private final PostRepository postRepository;
	private final UserRepository userRepository;

	@Transactional
	public PostDto.Response create(PostDto.Request request, String username) {
		User author = getUser(username);
		Post post = new Post(request.title(), request.content(), author);
		return PostDto.Response.from(postRepository.save(post));
	}

	public List<PostDto.Response> findAll() {
		return postRepository.findAll().stream()
		.map(PostDto.Response::from)
		.toList();
	}

	public PostDto.Response findById(Long id) {
		Post post = postRepository.findById(id)
			.orElseThrow(() -> new PostNotFoundException(id));
		return PostDto.Response.from(post);
	}

	@Transactional
	public PostDto.Response update(Long id, PostDto.Request request, String username) {
		Post post = postRepository.findById(id)
			.orElseThrow(() -> new PostNotFoundException(id));
		validateOwner(post.getAuthor().getUsername(), username);
		post.update(request.title(), request.content());
		return PostDto.Response.from(post);
	}

	@Transactional
	public void delete(Long id, String username) {
		Post post = postRepository.findById(id)
			.orElseThrow(() -> new PostNotFoundException(id));
		validateOwner(post.getAuthor().getUsername(), username);
		postRepository.delete(post);
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

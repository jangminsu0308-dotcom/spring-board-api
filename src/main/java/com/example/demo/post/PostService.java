package com.example.demo.post;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

	private final PostRepository postRepository;

	@Transactional
	public  PostDto.Response create(PostDto.Request request) {
		Post post = new Post(request.title(), request.content());
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
	public PostDto.Response update(Long id, PostDto.Request request) {
		Post post = postRepository.findById(id)
			.orElseThrow(() -> new PostNotFoundException(id));
		post.update(request.title(), request.content());
		return PostDto.Response.from(post);
	}

	@Transactional
	public void delete(Long id) {
		if (!postRepository.existsById(id)) {
			throw new PostNotFoundException(id);
		}
		postRepository.deleteById(id);
	}
}

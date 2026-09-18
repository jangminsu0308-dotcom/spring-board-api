package com.example.demo.post;

import com.example.demo.auth.User;
import com.example.demo.auth.UserRepository;
import com.example.demo.common.RateLimiterService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final RateLimiterService rateLimiter;

    private static final int MAX_COMMENTS_PER_MINUTE = 10;

    @Transactional
    public CommentDto.Response create(Long postId, CommentDto.Request request, String username) {
        rateLimiter.checkAllowed("comment:" + username, MAX_COMMENTS_PER_MINUTE, Duration.ofMinutes(1));
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));
        User author = getUser(username);
        Comment comment = new Comment(request.content(), author, post);
        return CommentDto.Response.from(commentRepository.save(comment));
    }

    private static final int MAX_PAGE_SIZE = 100;

    public CommentDto.PageResponse findByPost(Long postId, int page, int size) {
        if (!postRepository.existsById(postId)) {
            throw new PostNotFoundException(postId);
        }
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.ASC, "createdAt"));
        Page<Comment> result = commentRepository.findByPostIdOrderByCreatedAtAsc(postId, pageable);
        return CommentDto.PageResponse.from(result);
    }

    @Transactional
    public CommentDto.Response update(Long commentId, CommentDto.UpdateRequest request, String username) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException(commentId));
        validateOwner(comment.getAuthor().getUsername(), username);
        comment.update(request.content());
        return CommentDto.Response.from(comment);
    }

    @Transactional
    public void delete(Long commentId, String username) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException(commentId));
        validateOwner(comment.getAuthor().getUsername(), username);
        commentRepository.delete(comment);
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("인증된 사용자를 찾을 수 없습니다: " + username));
    }

    private void validateOwner(String ownerUsername, String requestUsername) {
        if (!ownerUsername.equals(requestUsername)) {
            throw new AccessDeniedException("본인이 작성한 댓글만 수정/삭제할 수 있습니다");
        }
    }
}

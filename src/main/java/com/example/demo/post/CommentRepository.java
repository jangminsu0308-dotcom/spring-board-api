package com.example.demo.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    /** author를 함께 조회해 댓글 목록 페이지당 N+1이 나지 않도록 한다. */
    @EntityGraph(attributePaths = "author")
    Page<Comment> findByPostIdOrderByCreatedAtAsc(Long postId, Pageable pageable);
}

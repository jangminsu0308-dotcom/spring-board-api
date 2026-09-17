package com.example.demo.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {

    /** author를 함께 조회해 목록 페이지당 N+1(작성자 지연 로딩)이 나지 않도록 한다. */
    @EntityGraph(attributePaths = "author")
    Page<Post> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "author")
    Page<Post> findByTitleContainingIgnoreCase(String keyword, Pageable pageable);
}

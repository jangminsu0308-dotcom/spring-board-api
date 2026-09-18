package com.example.demo.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    /** author를 함께 조회해 목록 페이지당 N+1(작성자 지연 로딩)이 나지 않도록 한다. */
    @EntityGraph(attributePaths = "author")
    Page<Post> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "author")
    Page<Post> findByTitleContainingIgnoreCase(String keyword, Pageable pageable);

    /**
     * 좋아요 개수는 Post의 컬럼이 아니라 post_likes를 세어야 나오는 값이라, id/title처럼
     * Sort.by(...)로는 정렬할 수 없다 — LEFT JOIN + GROUP BY로 직접 순서를 만든다.
     * 좋아요 수가 같으면 최신 글이 앞에 오도록 p.id를 2차 정렬 기준으로 둔다(동점이 많을 때
     * 페이지마다 순서가 흔들리는 것을 막기 위함).
     */
    @EntityGraph(attributePaths = "author")
    @Query("SELECT p FROM Post p LEFT JOIN PostLike pl ON pl.post = p GROUP BY p ORDER BY COUNT(pl) DESC, p.id DESC")
    Page<Post> findAllOrderByLikeCountDesc(Pageable pageable);

    @EntityGraph(attributePaths = "author")
    @Query("SELECT p FROM Post p LEFT JOIN PostLike pl ON pl.post = p "
            + "WHERE LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "GROUP BY p ORDER BY COUNT(pl) DESC, p.id DESC")
    Page<Post> findByTitleContainingIgnoreCaseOrderByLikeCountDesc(@Param("keyword") String keyword, Pageable pageable);
}

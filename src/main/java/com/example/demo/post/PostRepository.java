package com.example.demo.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    /**
     * 목록 조회를 전부 이 한 메서드로 처리한다 — keyword(제목+본문 검색)와 username(내 글만 보기)이
     * 각각 있을 수도 없을 수도 있어, 조합마다 메서드를 따로 만들면(검색 있음/없음 × 필터 있음/없음)
     * 개수가 배로 불어난다. ":param IS NULL OR ..." 패턴으로 "필터가 없으면 그 조건은 통과시킨다"를
     * 표현해 하나로 합쳤다. 정렬은 Pageable의 Sort를 그대로 쓴다.
     */
    @EntityGraph(attributePaths = "author")
    @Query("SELECT p FROM Post p WHERE "
            + "(:keyword IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "           OR LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
            + "AND (:username IS NULL OR p.author.username = :username)")
    Page<Post> search(@Param("keyword") String keyword, @Param("username") String username, Pageable pageable);

    /**
     * 좋아요 개수는 Post의 컬럼이 아니라 post_likes를 세어야 나오는 값이라, id/title처럼
     * Sort.by(...)로는 정렬할 수 없다 — LEFT JOIN + GROUP BY로 직접 순서를 만든다.
     * 좋아요 수가 같으면 최신 글이 앞에 오도록 p.id를 2차 정렬 기준으로 둔다(동점이 많을 때
     * 페이지마다 순서가 흔들리는 것을 막기 위함). 검색·필터 조건은 위 search()와 동일한 패턴.
     */
    @EntityGraph(attributePaths = "author")
    @Query("SELECT p FROM Post p LEFT JOIN PostLike pl ON pl.post = p WHERE "
            + "(:keyword IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "           OR LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
            + "AND (:username IS NULL OR p.author.username = :username) "
            + "GROUP BY p ORDER BY COUNT(pl) DESC, p.id DESC")
    Page<Post> searchOrderByLikeCountDesc(@Param("keyword") String keyword, @Param("username") String username, Pageable pageable);
}

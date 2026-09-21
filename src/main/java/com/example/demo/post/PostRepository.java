package com.example.demo.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    /**
     * 목록 조회를 전부 이 한 메서드로 처리한다 — keyword(제목+본문 검색)와 username(내 글만 보기)이
     * 각각 있을 수도 없을 수도 있어, 조합마다 메서드를 따로 만들면(검색 있음/없음 × 필터 있음/없음)
     * 개수가 배로 불어난다. ":param IS NULL OR ..." 패턴으로 "필터가 없으면 그 조건은 통과시킨다"를
     * 표현해 하나로 합쳤다.
     * <p>
     * 본문 검색은 LIKE '%keyword%'(leading wildcard) 대신 title/content의 FULLTEXT(ngram) 인덱스를
     * MATCH ... AGAINST로 사용한다 — 이 문법은 JPQL에 없는 MySQL 전용 문법이라 네이티브 쿼리로 쓴다.
     * 검색어를 이중 인용부호로 감싸 phrase search(BOOLEAN MODE)로 "이 문구를 포함한 글"을 찾는다.
     * ngram 최소 토큰 크기가 2글자라 한 글자짜리 검색어는 매칭되지 않는 게 알려진 한계다.
     * author는 @EntityGraph를 쓸 수 없어(네이티브 쿼리엔 적용 안 됨) users를 직접 JOIN해서
     * user_id로 필터링하되, author 자체는 여기서 함께 로딩하지 않는다 — PostService에서 이 페이지의
     * author id들을 한 번에 batch 조회해 세션에 미리 올려두는 방식으로 N+1을 피한다.
     */
    @Query(value = "SELECT p.* FROM posts p JOIN users u ON u.id = p.user_id WHERE "
            + "(:keyword IS NULL OR MATCH(p.title, p.content) AGAINST (CONCAT('\"', :keyword, '\"') IN BOOLEAN MODE)) "
            + "AND (:username IS NULL OR u.username = :username)",
        countQuery = "SELECT COUNT(*) FROM posts p JOIN users u ON u.id = p.user_id WHERE "
            + "(:keyword IS NULL OR MATCH(p.title, p.content) AGAINST (CONCAT('\"', :keyword, '\"') IN BOOLEAN MODE)) "
            + "AND (:username IS NULL OR u.username = :username)",
        nativeQuery = true)
    Page<Post> search(@Param("keyword") String keyword, @Param("username") String username, Pageable pageable);

    /**
     * 좋아요 개수는 Post의 컬럼이 아니라 post_likes를 세어야 나오는 값이라, id/title처럼
     * Sort.by(...)로는 정렬할 수 없다 — LEFT JOIN + GROUP BY로 직접 순서를 만든다.
     * 좋아요 수가 같으면 최신 글이 앞에 오도록 p.id를 2차 정렬 기준으로 둔다(동점이 많을 때
     * 페이지마다 순서가 흔들리는 것을 막기 위함). 검색·필터 조건은 위 search()와 동일한 패턴.
     */
    @Query(value = "SELECT p.* FROM posts p JOIN users u ON u.id = p.user_id "
            + "LEFT JOIN post_likes pl ON pl.post_id = p.id WHERE "
            + "(:keyword IS NULL OR MATCH(p.title, p.content) AGAINST (CONCAT('\"', :keyword, '\"') IN BOOLEAN MODE)) "
            + "AND (:username IS NULL OR u.username = :username) "
            + "GROUP BY p.id ORDER BY COUNT(pl.id) DESC, p.id DESC",
        countQuery = "SELECT COUNT(*) FROM posts p JOIN users u ON u.id = p.user_id WHERE "
            + "(:keyword IS NULL OR MATCH(p.title, p.content) AGAINST (CONCAT('\"', :keyword, '\"') IN BOOLEAN MODE)) "
            + "AND (:username IS NULL OR u.username = :username)",
        nativeQuery = true)
    Page<Post> searchOrderByLikeCountDesc(@Param("keyword") String keyword, @Param("username") String username, Pageable pageable);
}

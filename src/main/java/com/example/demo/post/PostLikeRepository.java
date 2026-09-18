package com.example.demo.post;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    Optional<PostLike> findByPostAndUser(Post post, com.example.demo.auth.User user);

    /** 응답 하나 만들자고 매번 User를 다시 조회하지 않도록, username으로 바로 존재 여부만 묻는다. */
    boolean existsByPostAndUser_Username(Post post, String username);

    long countByPost(Post post);

    /**
     * 목록 페이지 안의 게시글 여러 개를 한 번에 다룰 때, 게시글마다 countByPost를 따로 부르면
     * 또 N+1이 된다(6장/13장에서 이미 겪은 문제). 페이지에 있는 post id 전체를 한 번에 묶어
     * GROUP BY로 집계해 쿼리 1번으로 끝낸다.
     */
    @Query("SELECT pl.post.id, COUNT(pl) FROM PostLike pl WHERE pl.post.id IN :postIds GROUP BY pl.post.id")
    List<Object[]> countGroupedByPostIds(@Param("postIds") List<Long> postIds);

    @Query("SELECT pl.post.id FROM PostLike pl WHERE pl.user.username = :username AND pl.post.id IN :postIds")
    List<Long> findLikedPostIds(@Param("username") String username, @Param("postIds") List<Long> postIds);
}

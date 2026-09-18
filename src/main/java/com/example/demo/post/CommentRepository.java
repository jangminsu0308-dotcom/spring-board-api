package com.example.demo.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    /** author를 함께 조회해 댓글 목록 페이지당 N+1이 나지 않도록 한다. */
    @EntityGraph(attributePaths = "author")
    Page<Comment> findByPostIdOrderByCreatedAtAsc(Long postId, Pageable pageable);

    long countByPostId(Long postId);

    /** 게시글 목록에 댓글 개수를 보여줄 때, 게시글마다 countByPostId를 따로 부르면 N+1이 된다 —
     *  페이지에 있는 post id 전체를 한 번에 묶어 GROUP BY로 집계한다. */
    @Query("SELECT c.post.id, COUNT(c) FROM Comment c WHERE c.post.id IN :postIds GROUP BY c.post.id")
    List<Object[]> countGroupedByPostIds(@Param("postIds") List<Long> postIds);
}

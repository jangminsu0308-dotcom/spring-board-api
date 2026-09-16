package com.example.demo.post;

import com.example.demo.auth.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CommentRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private CommentRepository commentRepository;

    private User author;
    private Post post1;
    private Post post2;

    @BeforeEach
    void setUp() {
        author = em.persistAndFlush(new User("writer", "encoded"));
        post1 = em.persistAndFlush(new Post("게시글1", "내용1", author));
        post2 = em.persistAndFlush(new Post("게시글2", "내용2", author));
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));
    }

    @Test
    void findByPostIdOrderByCreatedAtAsc_작성순으로_반환한다() throws InterruptedException {
        Comment first = em.persistAndFlush(new Comment("첫 댓글", author, post1));
        Thread.sleep(10); // createdAt이 확실히 갈리도록 간격을 둔다
        Comment second = em.persistAndFlush(new Comment("둘째 댓글", author, post1));

        Page<Comment> result = commentRepository.findByPostIdOrderByCreatedAtAsc(post1.getId(), pageable(0, 10));

        assertThat(result.getContent()).extracting(Comment::getId)
                .containsExactly(first.getId(), second.getId());
    }

    @Test
    void findByPostIdOrderByCreatedAtAsc_다른_게시글의_댓글은_섞이지_않는다() {
        em.persistAndFlush(new Comment("post1 댓글", author, post1));
        em.persistAndFlush(new Comment("post2 댓글", author, post2));

        Page<Comment> result = commentRepository.findByPostIdOrderByCreatedAtAsc(post1.getId(), pageable(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getContent()).isEqualTo("post1 댓글");
    }

    @Test
    void findByPostIdOrderByCreatedAtAsc_댓글이_없으면_빈_페이지를_반환한다() {
        Page<Comment> result = commentRepository.findByPostIdOrderByCreatedAtAsc(post2.getId(), pageable(0, 10));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void findByPostIdOrderByCreatedAtAsc_페이지가_요청한_크기만큼만_반환된다() {
        for (int i = 1; i <= 15; i++) {
            em.persistAndFlush(new Comment("댓글 " + i, author, post1));
        }

        Page<Comment> firstPage = commentRepository.findByPostIdOrderByCreatedAtAsc(post1.getId(), pageable(0, 10));

        assertThat(firstPage.getContent()).hasSize(10);
        assertThat(firstPage.getTotalElements()).isEqualTo(15);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
    }
}

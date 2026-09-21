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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PostService 단위 테스트는 Repository를 목킹하므로, 실제 쿼리(특히 대소문자 무시 검색)가
 * MySQL에서도 의도대로 동작하는지는 검증하지 못한다. @DataJpaTest는 H2 대신 CI/로컬에
 * 구성된 실제 MySQL을 그대로 사용해(@AutoConfigureTestDatabase Replace.NONE) 이 간극을 메운다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private PostRepository postRepository;

    private User author;

    @BeforeEach
    void setUp() {
        author = em.persistAndFlush(new User("writer", "encoded"));
    }

    @Test
    void search_대소문자를_무시하고_제목_부분일치로_검색한다() {
        // 기존 DB 데이터와 절대 겹치지 않도록 매 실행마다 고유한 마커를 키워드로 쓴다.
        String marker = "MARKER-" + UUID.randomUUID();
        em.persistAndFlush(new Post(marker.toUpperCase() + " Boot 시작하기", "내용1", author));
        em.persistAndFlush(new Post(marker.toLowerCase() + " security 설정", "내용2", author));
        em.persistAndFlush(new Post("전혀 다른 제목", "내용3", author));

        Page<Post> result = postRepository.search(marker, null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).extracting(Post::getTitle)
                .allSatisfy(title -> assertThat(title.toUpperCase()).contains(marker.toUpperCase()));
    }

    @Test
    void search_제목에_없어도_본문에_있으면_찾는다() {
        String marker = "MARKER-" + UUID.randomUUID();
        em.persistAndFlush(new Post("평범한 제목", marker + " 라는 내용이 본문에만 있음", author));
        em.persistAndFlush(new Post("전혀 다른 제목", "전혀 다른 내용", author));

        Page<Post> result = postRepository.search(marker, null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getContent()).contains(marker);
    }

    @Test
    void search_일치하는_게_없으면_빈_페이지를_반환한다() {
        em.persistAndFlush(new Post("제목", "내용", author));

        Page<Post> result = postRepository.search("MARKER-존재안함-" + UUID.randomUUID(), null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void search_username을_지정하면_그_사람이_쓴_글만_반환한다() {
        String marker = "MARKER-" + UUID.randomUUID();
        User other = em.persistAndFlush(new User("other-" + UUID.randomUUID(), "encoded"));
        Post mine = em.persistAndFlush(new Post(marker, "내용", author));
        em.persistAndFlush(new Post(marker, "내용", other));

        Page<Post> result = postRepository.search(marker, author.getUsername(), PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(Post::getId).containsExactly(mine.getId());
    }

    @Test
    void searchOrderByLikeCountDesc_좋아요가_많은_순으로_정렬한다() {
        Post postA = em.persistAndFlush(new Post("좋아요 정렬 A", "내용", author));
        Post postB = em.persistAndFlush(new Post("좋아요 정렬 B", "내용", author));
        Post postC = em.persistAndFlush(new Post("좋아요 정렬 C", "내용", author));

        User liker1 = em.persistAndFlush(new User("liker1-" + UUID.randomUUID(), "encoded"));
        User liker2 = em.persistAndFlush(new User("liker2-" + UUID.randomUUID(), "encoded"));

        em.persistAndFlush(new PostLike(postB, liker1));
        em.persistAndFlush(new PostLike(postB, liker2));
        em.persistAndFlush(new PostLike(postC, liker1));
        // postA는 좋아요 없음

        Page<Post> result = postRepository.searchOrderByLikeCountDesc(null, null, PageRequest.of(0, 10));

        // Replace.NONE으로 기존 데이터가 섞여 있을 수 있으니, 우리가 만든 세 글의 상대적 순서만 확인한다.
        var orderOfOurPosts = result.getContent().stream()
                .map(Post::getId)
                .filter(id -> id.equals(postA.getId()) || id.equals(postB.getId()) || id.equals(postC.getId()))
                .toList();
        assertThat(orderOfOurPosts).containsExactly(postB.getId(), postC.getId(), postA.getId());
    }

    @Test
    void findAll_페이지가_요청한_크기만큼만_반환된다() {
        // Replace.NONE으로 개발 DB를 그대로 쓰므로, 기존에 남아있을 수 있는 데이터를 감안해 상대적으로 검증한다.
        long before = postRepository.count();
        for (int i = 1; i <= 15; i++) {
            em.persistAndFlush(new Post("글 " + i, "내용 " + i, author));
        }

        Page<Post> firstPage = postRepository.findAll(PageRequest.of(0, 10));

        assertThat(firstPage.getContent()).hasSize(10);
        assertThat(firstPage.getTotalElements()).isEqualTo(before + 15);
    }
}

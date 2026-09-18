package com.example.demo.post;

import com.example.demo.auth.User;
import com.example.demo.auth.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PostLikeRepository postLikeRepository;

    @Mock
    private CommentRepository commentRepository;

    @InjectMocks
    private PostService postService;

    private User author;
    private Post post;

    @BeforeEach
    void setUp() {
        author = new User("writer", "encoded");
        ReflectionTestUtils.setField(author, "id", 1L);

        post = new Post("제목", "내용", author);
        ReflectionTestUtils.setField(post, "id", 1L);
    }

    @Test
    void create_게시글을_저장하고_응답을_반환한다() {
        when(userRepository.findByUsername("writer")).thenReturn(Optional.of(author));
        when(postRepository.save(any(Post.class))).thenReturn(post);

        PostDto.Response response = postService.create(new PostDto.Request("제목", "내용"), "writer");

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.title()).isEqualTo("제목");
        assertThat(response.content()).isEqualTo("내용");
        assertThat(response.author()).isEqualTo("writer");
        assertThat(response.likeCount()).isEqualTo(0);
        assertThat(response.commentCount()).isEqualTo(0);
        verify(postRepository).save(any(Post.class));
    }

    @Test
    void findAll_키워드가_없으면_전체_목록을_페이지로_반환한다() {
        Page<Post> page = new PageImpl<>(List.of(post), PageRequest.of(0, 10), 1);
        when(postRepository.findAll(any(Pageable.class))).thenReturn(page);

        PostDto.PageResponse result = postService.findAll(0, 10, null, "latest", null);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).id()).isEqualTo(1L);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.page()).isEqualTo(0);
    }

    @Test
    void findAll_키워드가_있으면_제목으로_검색한다() {
        Page<Post> page = new PageImpl<>(List.of(post), PageRequest.of(0, 10), 1);
        when(postRepository.findByTitleContainingIgnoreCase(eq("제목"), any(Pageable.class))).thenReturn(page);

        PostDto.PageResponse result = postService.findAll(0, 10, "제목", "latest", null);

        assertThat(result.content()).hasSize(1);
        verify(postRepository).findByTitleContainingIgnoreCase(eq("제목"), any(Pageable.class));
        verify(postRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void findAll_sort가_oldest면_id_오름차순으로_정렬한다() {
        Page<Post> page = new PageImpl<>(List.of(post), PageRequest.of(0, 10), 1);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(postRepository.findAll(pageableCaptor.capture())).thenReturn(page);

        postService.findAll(0, 10, null, "oldest", null);

        Sort.Order order = pageableCaptor.getValue().getSort().getOrderFor("id");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void findAll_로그인한_사용자가_좋아요한_글은_likedByMe가_true다() {
        Page<Post> page = new PageImpl<>(List.of(post), PageRequest.of(0, 10), 1);
        when(postRepository.findAll(any(Pageable.class))).thenReturn(page);
        when(postLikeRepository.findLikedPostIds("writer", List.of(1L))).thenReturn(List.of(1L));
        when(postLikeRepository.countGroupedByPostIds(List.of(1L)))
                .thenReturn(List.<Object[]>of(new Object[]{1L, 3L}));

        PostDto.PageResponse result = postService.findAll(0, 10, null, "latest", "writer");

        assertThat(result.content().get(0).likedByMe()).isTrue();
        assertThat(result.content().get(0).likeCount()).isEqualTo(3L);
    }

    @Test
    void findAll_비로그인이면_likedByMe_조회_자체를_하지_않는다() {
        Page<Post> page = new PageImpl<>(List.of(post), PageRequest.of(0, 10), 1);
        when(postRepository.findAll(any(Pageable.class))).thenReturn(page);

        PostDto.PageResponse result = postService.findAll(0, 10, null, "latest", null);

        assertThat(result.content().get(0).likedByMe()).isFalse();
        verify(postLikeRepository, never()).findLikedPostIds(any(), any());
    }

    @Test
    void findById_존재하면_응답을_반환한다() {
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        PostDto.Response response = postService.findById(1L, null);

        assertThat(response.title()).isEqualTo("제목");
    }

    @Test
    void findById_존재하지_않으면_예외를_던진다() {
        when(postRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.findById(999L, null))
                .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    void update_본인_게시글이면_내용을_수정한다() {
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        PostDto.Response response = postService.update(1L, new PostDto.Request("수정된 제목", "수정된 내용"), "writer");

        assertThat(response.title()).isEqualTo("수정된 제목");
        assertThat(response.content()).isEqualTo("수정된 내용");
    }

    @Test
    void update_존재하지_않으면_예외를_던진다() {
        when(postRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.update(999L, new PostDto.Request("제목", "내용"), "writer"))
                .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    void update_본인_글이_아니면_예외를_던진다() {
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.update(1L, new PostDto.Request("제목", "내용"), "other"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void delete_본인_게시글이면_삭제한다() {
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        postService.delete(1L, "writer");

        verify(postRepository).delete(post);
    }

    @Test
    void delete_존재하지_않으면_예외를_던지고_삭제하지_않는다() {
        when(postRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.delete(999L, "writer"))
                .isInstanceOf(PostNotFoundException.class);
        verify(postRepository, never()).delete(any(Post.class));
    }

    @Test
    void delete_본인_글이_아니면_예외를_던지고_삭제하지_않는다() {
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.delete(1L, "other"))
                .isInstanceOf(AccessDeniedException.class);
        verify(postRepository, never()).delete(any(Post.class));
    }

    @Test
    void toggleLike_처음_누르면_좋아요를_저장하고_likedByMe는_true다() {
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(userRepository.findByUsername("reader")).thenReturn(Optional.of(author));
        when(postLikeRepository.findByPostAndUser(post, author)).thenReturn(Optional.empty());
        when(postLikeRepository.countByPost(post)).thenReturn(1L);

        PostDto.LikeResponse response = postService.toggleLike(1L, "reader");

        assertThat(response.likedByMe()).isTrue();
        assertThat(response.likeCount()).isEqualTo(1L);
        verify(postLikeRepository).save(any(PostLike.class));
    }

    @Test
    void toggleLike_이미_눌렀으면_취소하고_likedByMe는_false다() {
        PostLike existing = new PostLike(post, author);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(userRepository.findByUsername("reader")).thenReturn(Optional.of(author));
        when(postLikeRepository.findByPostAndUser(post, author)).thenReturn(Optional.of(existing));
        when(postLikeRepository.countByPost(post)).thenReturn(0L);

        PostDto.LikeResponse response = postService.toggleLike(1L, "reader");

        assertThat(response.likedByMe()).isFalse();
        assertThat(response.likeCount()).isEqualTo(0L);
        verify(postLikeRepository).delete(existing);
        verify(postLikeRepository, never()).save(any(PostLike.class));
    }

    @Test
    void toggleLike_게시글이_없으면_예외를_던진다() {
        when(postRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.toggleLike(999L, "reader"))
                .isInstanceOf(PostNotFoundException.class);
    }
}

package com.example.demo.post;

import com.example.demo.auth.User;
import com.example.demo.auth.UserRepository;
import com.example.demo.common.RateLimiterService;
import com.example.demo.common.TooManyRequestsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RateLimiterService rateLimiter;

    @InjectMocks
    private CommentService commentService;

    private User author;
    private Post post;
    private Comment comment;

    @BeforeEach
    void setUp() {
        author = new User("writer", "encoded");
        ReflectionTestUtils.setField(author, "id", 1L);

        post = new Post("제목", "내용", author);
        ReflectionTestUtils.setField(post, "id", 1L);

        comment = new Comment("댓글 내용", author, post);
        ReflectionTestUtils.setField(comment, "id", 10L);
    }

    @Test
    void create_게시글이_존재하면_댓글을_저장한다() {
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(userRepository.findByUsername("writer")).thenReturn(Optional.of(author));
        when(commentRepository.save(any(Comment.class))).thenReturn(comment);

        CommentDto.Response response = commentService.create(1L, new CommentDto.Request("댓글 내용"), "writer");

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.content()).isEqualTo("댓글 내용");
        assertThat(response.author()).isEqualTo("writer");
    }

    @Test
    void create_요청이_너무_많으면_예외를_던지고_저장하지_않는다() {
        doThrow(new TooManyRequestsException(30))
                .when(rateLimiter).checkAllowed(eq("comment:writer"), anyInt(), any());

        assertThatThrownBy(() -> commentService.create(1L, new CommentDto.Request("댓글"), "writer"))
                .isInstanceOf(TooManyRequestsException.class);
        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    void create_게시글이_없으면_예외를_던진다() {
        when(postRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.create(999L, new CommentDto.Request("댓글"), "writer"))
                .isInstanceOf(PostNotFoundException.class);
        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    void findByPost_게시글이_존재하면_댓글_페이지를_반환한다() {
        when(postRepository.existsById(1L)).thenReturn(true);
        Page<Comment> page = new PageImpl<>(List.of(comment), PageRequest.of(0, 10), 1);
        when(commentRepository.findByPostIdOrderByCreatedAtAsc(eq(1L), any(Pageable.class))).thenReturn(page);

        CommentDto.PageResponse result = commentService.findByPost(1L, 0, 10);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).id()).isEqualTo(10L);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void findByPost_게시글이_없으면_예외를_던진다() {
        when(postRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> commentService.findByPost(999L, 0, 10))
                .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    void update_본인_댓글이면_내용을_수정한다() {
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));

        CommentDto.Response response = commentService.update(10L, new CommentDto.UpdateRequest("수정된 댓글"), "writer");

        assertThat(response.content()).isEqualTo("수정된 댓글");
    }

    @Test
    void update_존재하지_않으면_예외를_던진다() {
        when(commentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.update(999L, new CommentDto.UpdateRequest("내용"), "writer"))
                .isInstanceOf(CommentNotFoundException.class);
    }

    @Test
    void update_본인_댓글이_아니면_예외를_던진다() {
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.update(10L, new CommentDto.UpdateRequest("내용"), "other"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void delete_본인_댓글이면_삭제한다() {
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));

        commentService.delete(10L, "writer");

        verify(commentRepository).delete(comment);
    }

    @Test
    void delete_존재하지_않으면_예외를_던지고_삭제하지_않는다() {
        when(commentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.delete(999L, "writer"))
                .isInstanceOf(CommentNotFoundException.class);
        verify(commentRepository, never()).delete(any(Comment.class));
    }

    @Test
    void delete_본인_댓글이_아니면_예외를_던지고_삭제하지_않는다() {
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.delete(10L, "other"))
                .isInstanceOf(AccessDeniedException.class);
        verify(commentRepository, never()).delete(any(Comment.class));
    }
}

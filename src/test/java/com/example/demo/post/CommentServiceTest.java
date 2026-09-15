package com.example.demo.post;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private PostRepository postRepository;

    @InjectMocks
    private CommentService commentService;

    private Post post;
    private Comment comment;

    @BeforeEach
    void setUp() {
        post = new Post("제목", "내용");
        ReflectionTestUtils.setField(post, "id", 1L);

        comment = new Comment("댓글 내용", "작성자", post);
        ReflectionTestUtils.setField(comment, "id", 10L);
    }

    @Test
    void create_게시글이_존재하면_댓글을_저장한다() {
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(commentRepository.save(any(Comment.class))).thenReturn(comment);

        CommentDto.Response response = commentService.create(1L, new CommentDto.Request("댓글 내용", "작성자"));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.content()).isEqualTo("댓글 내용");
        assertThat(response.author()).isEqualTo("작성자");
    }

    @Test
    void create_게시글이_없으면_예외를_던진다() {
        when(postRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.create(999L, new CommentDto.Request("댓글", "작성자")))
                .isInstanceOf(PostNotFoundException.class);
        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    void findByPost_게시글이_존재하면_댓글_목록을_반환한다() {
        when(postRepository.existsById(1L)).thenReturn(true);
        when(commentRepository.findByPostIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(comment));

        List<CommentDto.Response> result = commentService.findByPost(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(10L);
    }

    @Test
    void findByPost_게시글이_없으면_예외를_던진다() {
        when(postRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> commentService.findByPost(999L))
                .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    void update_존재하면_내용을_수정한다() {
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));

        CommentDto.Response response = commentService.update(10L, new CommentDto.UpdateRequest("수정된 댓글"));

        assertThat(response.content()).isEqualTo("수정된 댓글");
    }

    @Test
    void update_존재하지_않으면_예외를_던진다() {
        when(commentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.update(999L, new CommentDto.UpdateRequest("내용")))
                .isInstanceOf(CommentNotFoundException.class);
    }

    @Test
    void delete_존재하면_삭제한다() {
        when(commentRepository.existsById(10L)).thenReturn(true);

        commentService.delete(10L);

        verify(commentRepository).deleteById(10L);
    }

    @Test
    void delete_존재하지_않으면_예외를_던지고_삭제하지_않는다() {
        when(commentRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> commentService.delete(999L))
                .isInstanceOf(CommentNotFoundException.class);
        verify(commentRepository, never()).deleteById(anyLong());
    }
}

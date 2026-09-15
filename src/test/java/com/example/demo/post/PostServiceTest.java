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
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @InjectMocks
    private PostService postService;

    private Post post;

    @BeforeEach
    void setUp() {
        post = new Post("제목", "내용");
        ReflectionTestUtils.setField(post, "id", 1L);
    }

    @Test
    void create_게시글을_저장하고_응답을_반환한다() {
        when(postRepository.save(any(Post.class))).thenReturn(post);

        PostDto.Response response = postService.create(new PostDto.Request("제목", "내용"));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.title()).isEqualTo("제목");
        assertThat(response.content()).isEqualTo("내용");
        verify(postRepository).save(any(Post.class));
    }

    @Test
    void findAll_전체_게시글을_응답으로_변환해_반환한다() {
        when(postRepository.findAll()).thenReturn(List.of(post));

        List<PostDto.Response> result = postService.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
    }

    @Test
    void findById_존재하면_응답을_반환한다() {
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        PostDto.Response response = postService.findById(1L);

        assertThat(response.title()).isEqualTo("제목");
    }

    @Test
    void findById_존재하지_않으면_예외를_던진다() {
        when(postRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.findById(999L))
                .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    void update_존재하면_내용을_수정한다() {
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        PostDto.Response response = postService.update(1L, new PostDto.Request("수정된 제목", "수정된 내용"));

        assertThat(response.title()).isEqualTo("수정된 제목");
        assertThat(response.content()).isEqualTo("수정된 내용");
    }

    @Test
    void update_존재하지_않으면_예외를_던진다() {
        when(postRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.update(999L, new PostDto.Request("제목", "내용")))
                .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    void delete_존재하면_삭제한다() {
        when(postRepository.existsById(1L)).thenReturn(true);

        postService.delete(1L);

        verify(postRepository).deleteById(1L);
    }

    @Test
    void delete_존재하지_않으면_예외를_던지고_삭제하지_않는다() {
        when(postRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> postService.delete(999L))
                .isInstanceOf(PostNotFoundException.class);
        verify(postRepository, never()).deleteById(anyLong());
    }
}

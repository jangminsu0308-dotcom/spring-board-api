package com.example.demo.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PostController.class)
class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private PostService postService;

    @Test
    void 게시글_생성_성공시_201과_Location을_반환한다() throws Exception {
        PostDto.Response response = new PostDto.Response(1L, "제목", "내용", LocalDateTime.now(), LocalDateTime.now());
        when(postService.create(any(PostDto.Request.class))).thenReturn(response);

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PostDto.Request("제목", "내용"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/posts/1"))
                .andExpect(jsonPath("$.title").value("제목"));
    }

    @Test
    void 게시글_생성시_제목이_비어있으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PostDto.Request("", "내용"))))
                .andExpect(status().isBadRequest());

        verify(postService, never()).create(any());
    }

    @Test
    void 게시글_목록_조회() throws Exception {
        PostDto.Response response = new PostDto.Response(1L, "제목", "내용", LocalDateTime.now(), LocalDateTime.now());
        when(postService.findAll()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void 존재하지_않는_게시글_조회시_404를_반환한다() throws Exception {
        when(postService.findById(999L)).thenThrow(new PostNotFoundException(999L));

        mockMvc.perform(get("/api/posts/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("게시글을 찾을 수 없습니다: 999"));
    }

    @Test
    void 게시글_수정() throws Exception {
        PostDto.Response response = new PostDto.Response(1L, "수정된 제목", "수정된 내용", LocalDateTime.now(), LocalDateTime.now());
        when(postService.update(eq(1L), any(PostDto.Request.class))).thenReturn(response);

        mockMvc.perform(put("/api/posts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PostDto.Request("수정된 제목", "수정된 내용"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("수정된 제목"));
    }

    @Test
    void 게시글_삭제시_204를_반환한다() throws Exception {
        mockMvc.perform(delete("/api/posts/1"))
                .andExpect(status().isNoContent());

        verify(postService).delete(1L);
    }
}

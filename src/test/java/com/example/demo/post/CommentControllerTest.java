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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommentController.class)
class CommentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private CommentService commentService;

    @Test
    void 댓글_작성_성공시_201과_Location을_반환한다() throws Exception {
        CommentDto.Response response = new CommentDto.Response(1L, "댓글", "작성자", LocalDateTime.now());
        when(commentService.create(eq(1L), any(CommentDto.Request.class))).thenReturn(response);

        mockMvc.perform(post("/api/posts/1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommentDto.Request("댓글", "작성자"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/comments/1"))
                .andExpect(jsonPath("$.author").value("작성자"));
    }

    @Test
    void 댓글_작성시_내용이_비어있으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/posts/1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommentDto.Request("", "작성자"))))
                .andExpect(status().isBadRequest());

        verify(commentService, never()).create(anyLong(), any());
    }

    @Test
    void 게시글의_댓글_목록_조회() throws Exception {
        CommentDto.Response response = new CommentDto.Response(1L, "댓글", "작성자", LocalDateTime.now());
        when(commentService.findByPost(1L)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/posts/1/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void 존재하지_않는_게시글의_댓글_조회시_404를_반환한다() throws Exception {
        when(commentService.findByPost(999L)).thenThrow(new PostNotFoundException(999L));

        mockMvc.perform(get("/api/posts/999/comments"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 댓글_수정() throws Exception {
        CommentDto.Response response = new CommentDto.Response(1L, "수정된 댓글", "작성자", LocalDateTime.now());
        when(commentService.update(eq(1L), any(CommentDto.UpdateRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/comments/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommentDto.UpdateRequest("수정된 댓글"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("수정된 댓글"));
    }

    @Test
    void 존재하지_않는_댓글_수정시_404를_반환한다() throws Exception {
        when(commentService.update(eq(999L), any(CommentDto.UpdateRequest.class)))
                .thenThrow(new CommentNotFoundException(999L));

        mockMvc.perform(put("/api/comments/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommentDto.UpdateRequest("내용"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void 댓글_삭제시_204를_반환한다() throws Exception {
        mockMvc.perform(delete("/api/comments/1"))
                .andExpect(status().isNoContent());

        verify(commentService).delete(1L);
    }
}

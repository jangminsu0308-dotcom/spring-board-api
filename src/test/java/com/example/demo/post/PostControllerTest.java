package com.example.demo.post;

import com.example.demo.common.NtfyNotifier;
import com.example.demo.security.JwtAuthenticationEntryPoint;
import com.example.demo.security.JwtTokenProvider;
import com.example.demo.security.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PostController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtTokenProvider.class, NtfyNotifier.class})
class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private PostService postService;

    private static PostDto.Response response(Long id, String title, String content, String author) {
        return new PostDto.Response(id, title, content, author, LocalDateTime.now(), LocalDateTime.now(), 0, false, 0);
    }

    @Test
    void 게시글_생성_성공시_201과_Location을_반환한다() throws Exception {
        when(postService.create(any(PostDto.Request.class), eq("writer")))
                .thenReturn(response(1L, "제목", "내용", "writer"));

        mockMvc.perform(post("/api/posts")
                        .with(user("writer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PostDto.Request("제목", "내용"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/posts/1"))
                .andExpect(jsonPath("$.title").value("제목"))
                .andExpect(jsonPath("$.author").value("writer"));
    }

    @Test
    void 게시글_생성시_인증이_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PostDto.Request("제목", "내용"))))
                .andExpect(status().isUnauthorized());

        verify(postService, never()).create(any(), any());
    }

    @Test
    void 게시글_생성시_제목이_비어있으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .with(user("writer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PostDto.Request("", "내용"))))
                .andExpect(status().isBadRequest());

        verify(postService, never()).create(any(), any());
    }

    @Test
    void 게시글_목록_조회() throws Exception {
        PostDto.PageResponse pageResponse = new PostDto.PageResponse(List.of(response(1L, "제목", "내용", "writer")), 0, 10, 1, 1);
        when(postService.findAll(0, 10, null, "latest", false, null)).thenReturn(pageResponse);

        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void 게시글_목록_조회시_page_size_keyword_sort를_그대로_전달한다() throws Exception {
        PostDto.PageResponse pageResponse = new PostDto.PageResponse(List.of(), 2, 5, 0, 0);
        when(postService.findAll(2, 5, "제목", "oldest", false, null)).thenReturn(pageResponse);

        mockMvc.perform(get("/api/posts").param("page", "2").param("size", "5")
                        .param("keyword", "제목").param("sort", "oldest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(5));

        verify(postService).findAll(2, 5, "제목", "oldest", false, null);
    }

    @Test
    void 게시글_목록_조회시_로그인_상태면_사용자명을_함께_전달한다() throws Exception {
        PostDto.PageResponse pageResponse = new PostDto.PageResponse(List.of(), 0, 10, 0, 0);
        when(postService.findAll(0, 10, null, "latest", false, "writer")).thenReturn(pageResponse);

        mockMvc.perform(get("/api/posts").with(user("writer")))
                .andExpect(status().isOk());

        verify(postService).findAll(0, 10, null, "latest", false, "writer");
    }

    @Test
    void 게시글_목록_조회시_mine_파라미터를_그대로_전달한다() throws Exception {
        PostDto.PageResponse pageResponse = new PostDto.PageResponse(List.of(), 0, 10, 0, 0);
        when(postService.findAll(0, 10, null, "latest", true, "writer")).thenReturn(pageResponse);

        mockMvc.perform(get("/api/posts").param("mine", "true").with(user("writer")))
                .andExpect(status().isOk());

        verify(postService).findAll(0, 10, null, "latest", true, "writer");
    }

    @Test
    void 존재하지_않는_게시글_조회시_404를_반환한다() throws Exception {
        when(postService.findById(eq(999L), isNull())).thenThrow(new PostNotFoundException(999L));

        mockMvc.perform(get("/api/posts/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("게시글을 찾을 수 없습니다: 999"));
    }

    @Test
    void 게시글_조회시_id가_숫자가_아니면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/posts/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void 게시글_수정() throws Exception {
        when(postService.update(eq(1L), any(PostDto.Request.class), eq("writer")))
                .thenReturn(response(1L, "수정된 제목", "수정된 내용", "writer"));

        mockMvc.perform(put("/api/posts/1")
                        .with(user("writer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PostDto.Request("수정된 제목", "수정된 내용"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("수정된 제목"));
    }

    @Test
    void 게시글_삭제시_204를_반환한다() throws Exception {
        mockMvc.perform(delete("/api/posts/1").with(user("writer")))
                .andExpect(status().isNoContent());

        verify(postService).delete(1L, "writer");
    }

    @Test
    void 좋아요_토글_성공시_결과를_반환한다() throws Exception {
        when(postService.toggleLike(1L, "reader")).thenReturn(new PostDto.LikeResponse(4L, true));

        mockMvc.perform(post("/api/posts/1/like").with(user("reader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(4))
                .andExpect(jsonPath("$.likedByMe").value(true));
    }

    @Test
    void 좋아요_토글시_인증이_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(post("/api/posts/1/like"))
                .andExpect(status().isUnauthorized());

        verify(postService, never()).toggleLike(any(), any());
    }
}

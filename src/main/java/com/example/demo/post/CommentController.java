package com.example.demo.post;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

import static com.example.demo.common.OpenApiConfig.BEARER_AUTH;

@Tag(name = "댓글", description = "댓글 CRUD API")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @Operation(summary = "댓글 작성", description = "로그인이 필요합니다.")
    @SecurityRequirement(name = BEARER_AUTH)
    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<CommentDto.Response> create(
            @PathVariable Long postId,
            @Valid @RequestBody CommentDto.Request request,
            Authentication authentication) {
        CommentDto.Response response = commentService.create(postId, request, authentication.getName());
        return ResponseEntity.created(URI.create("/api/comments/" + response.id())).body(response);
    }

    @Operation(summary = "게시글의 댓글 목록 조회", description = "page(0부터), size로 조회합니다.")
    @GetMapping("/posts/{postId}/comments")
    public CommentDto.PageResponse findByPost(
            @PathVariable Long postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return commentService.findByPost(postId, page, size);
    }

    @Operation(summary = "댓글 수정", description = "본인이 작성한 댓글만 수정할 수 있습니다.")
    @SecurityRequirement(name = BEARER_AUTH)
    @PutMapping("/comments/{commentId}")
    public CommentDto.Response update(
            @PathVariable Long commentId,
            @Valid @RequestBody CommentDto.UpdateRequest request,
            Authentication authentication) {
        return commentService.update(commentId, request, authentication.getName());
    }

    @Operation(summary = "댓글 삭제", description = "본인이 작성한 댓글만 삭제할 수 있습니다.")
    @SecurityRequirement(name = BEARER_AUTH)
    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> delete(@PathVariable Long commentId, Authentication authentication) {
        commentService.delete(commentId, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}

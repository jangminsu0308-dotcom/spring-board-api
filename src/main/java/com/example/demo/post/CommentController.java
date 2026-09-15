package com.example.demo.post;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@Tag(name = "댓글", description = "댓글 CRUD API")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @Operation(summary = "댓글 작성")
    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<CommentDto.Response> create(
            @PathVariable Long postId,
            @Valid @RequestBody CommentDto.Request request) {
        CommentDto.Response response = commentService.create(postId, request);
        return ResponseEntity.created(URI.create("/api/comments/" + response.id())).body(response);
    }

    @Operation(summary = "게시글의 댓글 목록 조회")
    @GetMapping("/posts/{postId}/comments")
    public List<CommentDto.Response> findByPost(@PathVariable Long postId) {
        return commentService.findByPost(postId);
    }

    @Operation(summary = "댓글 수정")
    @PutMapping("/comments/{commentId}")
    public CommentDto.Response update(
            @PathVariable Long commentId,
            @Valid @RequestBody CommentDto.UpdateRequest request) {
        return commentService.update(commentId, request);
    }

    @Operation(summary = "댓글 삭제")
    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> delete(@PathVariable Long commentId) {
        commentService.delete(commentId);
        return ResponseEntity.noContent().build();
    }
}

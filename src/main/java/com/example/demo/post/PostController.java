package com.example.demo.post;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "게시글", description = "게시글 CRUD API")
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @Operation(summary = "게시글 생성", description = "새로운 게시글을 생성합니다.")
    @PostMapping
    public ResponseEntity<PostDto.Response> create(@Valid @RequestBody PostDto.Request request) {
        PostDto.Response response = postService.create(request);
        return ResponseEntity.created(URI.create("/api/posts/" + response.id())).body(response);
    }

    @Operation (summary = "게시글 목록 조회")
    @GetMapping
    public List<PostDto.Response> findAll() {
        return postService.findAll();
    }

    @Operation(summary = "게시글 단건 조회")
    @GetMapping("/{id}")
    public PostDto.Response findById(@PathVariable Long id) {
        return postService.findById(id);
    }

    @Operation(summary = "게시글 수정")
    @PutMapping("/{id}")
    public PostDto.Response update(@PathVariable Long id, @Valid @RequestBody PostDto.Request request) {
        return postService.update(id, request);
    }

    @Operation(summary = "게시글 삭제")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        postService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

package com.example.demo.post;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping
    public ResponseEntity<PostDto.Response> create(@Valid @RequestBody PostDto.Request request) {
        PostDto.Response response = postService.create(request);
        return ResponseEntity.created(URI.create("/api/posts/" + response.id())).body(response);
    }

    @GetMapping
    public List<PostDto.Response> findAll() {
        return postService.findAll();
    }

    @GetMapping("/{id}")
    public PostDto.Response findById(@PathVariable Long id) {
        return postService.findById(id);
    }

    @PutMapping("/{id}")
    public PostDto.Response update(@PathVariable Long id, @Valid @RequestBody PostDto.Request request) {
        return postService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        postService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

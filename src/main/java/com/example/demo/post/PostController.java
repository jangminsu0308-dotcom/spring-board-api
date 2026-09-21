package com.example.demo.post;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import static com.example.demo.common.OpenApiConfig.BEARER_AUTH;

@Tag(name = "게시글", description = "게시글 CRUD API")
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @Operation(summary = "게시글 생성", description = "새로운 게시글을 생성합니다. 로그인이 필요합니다.")
    @SecurityRequirement(name = BEARER_AUTH)
    @PostMapping
    public ResponseEntity<PostDto.Response> create(@Valid @RequestBody PostDto.Request request, Authentication authentication) {
        PostDto.Response response = postService.create(request, authentication.getName());
        return ResponseEntity.created(URI.create("/api/posts/" + response.id())).body(response);
    }

    @Operation(summary = "게시글 목록 조회", description = "page(0부터), size, keyword(제목+본문 검색), sort(latest/oldest/title/popular)로 조회합니다. "
            + "mine=true면 로그인한 사용자가 작성한 글만 보여줍니다(비로그인이면 무시됩니다). "
            + "로그인 상태로 요청하면 각 게시글에 내가 좋아요를 눌렀는지(likedByMe)도 함께 내려줍니다.")
    @GetMapping
    public PostDto.PageResponse findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "false") boolean mine,
            Authentication authentication) {
        return postService.findAll(page, size, keyword, sort, mine, usernameOrNull(authentication));
    }

    @Operation(summary = "게시글 단건 조회", description = "조회할 때마다 조회수가 1 증가합니다(같은 사람이 짧은 시간 안에 다시 보면 중복으로 세지 않습니다).")
    @GetMapping("/{id}")
    public PostDto.Response findById(@PathVariable Long id, Authentication authentication, HttpServletRequest request) {
        String username = usernameOrNull(authentication);
        String viewerKey = username != null ? username : request.getRemoteAddr();
        return postService.findById(id, username, viewerKey);
    }

    @Operation(summary = "게시글 수정", description = "본인이 작성한 게시글만 수정할 수 있습니다.")
    @SecurityRequirement(name = BEARER_AUTH)
    @PutMapping("/{id}")
    public PostDto.Response update(@PathVariable Long id, @Valid @RequestBody PostDto.Request request, Authentication authentication) {
        return postService.update(id, request, authentication.getName());
    }

    @Operation(summary = "게시글 삭제", description = "본인이 작성한 게시글만 삭제할 수 있습니다.")
    @SecurityRequirement(name = BEARER_AUTH)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        postService.delete(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "좋아요 토글", description = "이미 눌렀으면 취소하고, 안 눌렀으면 좋아요를 남깁니다. 로그인이 필요합니다.")
    @SecurityRequirement(name = BEARER_AUTH)
    @PostMapping("/{id}/like")
    public PostDto.LikeResponse toggleLike(@PathVariable Long id, Authentication authentication) {
        return postService.toggleLike(id, authentication.getName());
    }

    /** GET /api/posts는 비로그인도 허용되는 경로라, 토큰이 없으면 인증이 아예 안 된 게 아니라
     *  "익명 사용자"로 채워진다 — 실제 로그인 여부는 이 타입으로 구분해야 한다. */
    private String usernameOrNull(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return authentication.getName();
    }
}

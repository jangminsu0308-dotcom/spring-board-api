-- 게시글별 댓글을 작성순으로 페이징 조회할 때(findByPostIdOrderByCreatedAtAsc), post_id 단일 인덱스로는
-- WHERE 조건만 인덱스를 타고 ORDER BY created_at은 filesort로 처리된다.
-- (post_id, created_at) 복합 인덱스를 추가하면 필터링과 정렬을 인덱스 하나로 해결한다.

CREATE INDEX idx_comments_post_id_created_at ON comments (post_id, created_at);

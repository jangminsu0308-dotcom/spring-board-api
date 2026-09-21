-- 게시글 상세 조회수. 상세 화면(GET /api/posts/{id})을 볼 때마다 증가시킨다.
ALTER TABLE posts ADD COLUMN view_count BIGINT NOT NULL DEFAULT 0;

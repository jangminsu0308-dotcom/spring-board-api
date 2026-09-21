-- 지금까지 본문 검색은 LIKE '%keyword%' 방식이라 title/content에 인덱스가 있어도 타지 못하고
-- 매번 전체 테이블을 스캔한다(leading wildcard라 B-tree 인덱스를 못 씀). 게시글이 많아지면
-- 검색이 느려진다.
-- MySQL FULLTEXT 인덱스로 바꿔 MATCH ... AGAINST로 검색하면 인덱스를 탄다.
-- 기본 파서는 공백 기준으로 단어를 나눠서 한글(띄어쓰기 없이 붙는 언어)엔 거의 안 맞는다 —
-- ngram 파서는 글자를 n개씩(기본 2글자, ngram_token_size 기본값) 묶어 인덱싱해 한글에도 쓸 수 있다.
ALTER TABLE posts ADD FULLTEXT INDEX ft_posts_title_content (title, content) WITH PARSER ngram;

-- 8주차 즐겨찾기 테이블. ddl-auto=validate라 서버 실행 전에 한 번 실행해야 해요.
CREATE TABLE IF NOT EXISTS favorite (
    favorite_id BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_id   BIGINT      NOT NULL,
    movie_id    BIGINT      NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    CONSTRAINT uk_favorite_member_movie UNIQUE (member_id, movie_id),
    CONSTRAINT fk_favorite_member FOREIGN KEY (member_id) REFERENCES member (member_id) ON DELETE CASCADE
);

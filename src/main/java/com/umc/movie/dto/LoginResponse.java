package com.umc.movie.dto;

public record LoginResponse(
        Long memberId, // 평점 조회(GET /members/{memberId}/ratings)에 필요해서 함께 내려요
        String nickname,
        String accessToken
) {
}

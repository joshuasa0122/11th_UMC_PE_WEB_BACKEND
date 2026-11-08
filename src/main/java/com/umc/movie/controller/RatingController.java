package com.umc.movie.controller;

import com.umc.movie.dto.ApiResponse;
import com.umc.movie.dto.CreateRatingRequest;
import com.umc.movie.dto.RatingResponse;
import com.umc.movie.dto.UpdateRatingRequest;
import com.umc.movie.resolver.LoginMember;
import com.umc.movie.service.RatingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class RatingController {

    private final RatingService ratingService;

    // 유저별 평점 조회는 공개 API라 토큰을 받지 않아요.
    @GetMapping("/members/{memberId}/ratings")
    public List<RatingResponse> getMemberRatings(@PathVariable Long memberId) {
        return ratingService.getRatingsByMember(memberId);
    }

    // memberId는 URL이 아니라 @LoginMember로 토큰의 로그인 회원 값을 받아요.
    @PostMapping("/ratings")
    public ResponseEntity<ApiResponse<RatingResponse>> create(
            @LoginMember Long memberId,
            @Valid @RequestBody CreateRatingRequest request
    ) {
        RatingResponse response = ratingService.create(memberId, request);
        return ResponseEntity.status(201).body(ApiResponse.created("평점을 등록했습니다.", response));
    }

    @PatchMapping("/ratings/{ratingId}")
    public ResponseEntity<ApiResponse<RatingResponse>> update(
            @LoginMember Long memberId,
            @PathVariable Long ratingId,
            @Valid @RequestBody UpdateRatingRequest request
    ) {
        RatingResponse response = ratingService.update(memberId, ratingId, request);
        return ResponseEntity.ok(ApiResponse.ok("평점을 수정했습니다.", response));
    }

    // 204는 본문을 보낼 수 없어서 공통 응답 형식을 지키려고 200 + result null로 응답해요.
    @DeleteMapping("/ratings/{ratingId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @LoginMember Long memberId,
            @PathVariable Long ratingId
    ) {
        ratingService.delete(memberId, ratingId);
        return ResponseEntity.ok(ApiResponse.ok("평점을 삭제했습니다.", null));
    }
}

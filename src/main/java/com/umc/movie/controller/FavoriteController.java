package com.umc.movie.controller;

import com.umc.movie.dto.ApiResponse;
import com.umc.movie.dto.CreateFavoriteRequest;
import com.umc.movie.dto.FavoriteResponse;
import com.umc.movie.dto.FavoriteStatusResponse;
import com.umc.movie.resolver.LoginMember;
import com.umc.movie.service.FavoriteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 즐겨찾기는 모두 로그인한 회원 기준이라 memberId를 URL이 아니라 토큰(@LoginMember)에서 받아요.
@RestController
@RequiredArgsConstructor
@RequestMapping("/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<FavoriteResponse>>> getMyFavorites(@LoginMember Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok("즐겨찾기 목록을 조회했습니다.", favoriteService.getMyFavorites(memberId)));
    }

    @GetMapping("/{movieId}")
    public ResponseEntity<ApiResponse<FavoriteStatusResponse>> getStatus(@LoginMember Long memberId, @PathVariable Long movieId) {
        return ResponseEntity.ok(ApiResponse.ok("즐겨찾기 여부를 조회했습니다.", favoriteService.getStatus(memberId, movieId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<FavoriteResponse>> add(@LoginMember Long memberId, @Valid @RequestBody CreateFavoriteRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.created("즐겨찾기에 추가했습니다.", favoriteService.add(memberId, request.movieId())));
    }

    // 평점 삭제와 같이 공통 응답 형식을 지키려고 200 + result null로 응답해요.
    @DeleteMapping("/{movieId}")
    public ResponseEntity<ApiResponse<Void>> remove(@LoginMember Long memberId, @PathVariable Long movieId) {
        favoriteService.remove(memberId, movieId);
        return ResponseEntity.ok(ApiResponse.ok("즐겨찾기를 해제했습니다.", null));
    }
}

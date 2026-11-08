package com.umc.movie.service;

import com.umc.movie.dto.CreateRatingRequest;
import com.umc.movie.dto.RatingResponse;
import com.umc.movie.dto.UpdateRatingRequest;
import com.umc.movie.entity.Member;
import com.umc.movie.entity.Rating;
import com.umc.movie.exception.ApiException;
import com.umc.movie.repository.RatingRepository;
import com.umc.movie.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RatingService {

    private final RatingRepository ratingRepository;
    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public List<RatingResponse> getRatingsByMember(Long memberId) {
        if (!memberRepository.existsById(memberId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEMBER404_1", "해당 회원을 찾을 수 없습니다.");
        }

        return ratingRepository.findByMember_MemberIdOrderByRatingIdDesc(memberId).stream()
                .map(RatingResponse::from)
                .toList();
    }

    @Transactional
    public RatingResponse create(Long memberId, CreateRatingRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEMBER404_1", "해당 회원을 찾을 수 없습니다."));

        // (member_id, movie_id) 복합 UNIQUE 제약이 DB에서도 막아주지만, 정한 메시지로 응답하려고 먼저 확인해요.
        if (ratingRepository.existsByMember_MemberIdAndMovieId(memberId, request.movieId())) {
            throw new ApiException(HttpStatus.CONFLICT, "RATING409_1", "이미 이 영화에 평점을 남기셨습니다.");
        }

        Rating rating = Rating.builder()
                .member(member)
                .movieId(request.movieId())
                .score(request.score())
                .comment(request.comment())
                .build();

        return RatingResponse.from(ratingRepository.save(rating));
    }

    // 토큰에서 꺼낸 요청자(memberId)가 모든 동작의 주어라서 인자 맨 앞에 둬요.
    @Transactional
    public RatingResponse update(Long memberId, Long ratingId, UpdateRatingRequest request) {
        Rating rating = findOwnedRating(memberId, ratingId);
        rating.update(request.score(), request.comment());
        ratingRepository.save(rating);
        return RatingResponse.from(rating);
    }

    @Transactional
    public void delete(Long memberId, Long ratingId) {
        Rating rating = findOwnedRating(memberId, ratingId);
        ratingRepository.delete(rating);
    }

    // 평점이 없으면 404, 요청자의 평점이 아니면 403이에요. 수정과 삭제가 같은 검사를 해서 묶었어요.
    private Rating findOwnedRating(Long memberId, Long ratingId) {
        Rating rating = ratingRepository.findById(ratingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RATING404_1", "해당 평점을 찾을 수 없습니다."));

        if (!rating.getMember().getMemberId().equals(memberId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "RATING403_1", "본인이 작성한 평점만 수정하거나 삭제할 수 있습니다.");
        }

        return rating;
    }
}

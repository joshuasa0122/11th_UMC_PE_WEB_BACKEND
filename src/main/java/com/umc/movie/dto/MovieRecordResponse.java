package com.umc.movie.dto;

// addToFavorites가 false면 favorite은 null이에요.
public record MovieRecordResponse(
        RatingResponse rating,
        FavoriteResponse favorite
) {
}

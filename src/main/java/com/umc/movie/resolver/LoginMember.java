package com.umc.movie.resolver;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// 컨트롤러 파라미터에 붙이면 토큰에서 꺼낸 로그인 회원의 memberId가 들어와요.
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginMember {
}

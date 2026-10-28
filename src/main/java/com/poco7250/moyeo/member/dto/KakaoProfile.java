package com.poco7250.moyeo.member.dto;

/**
 * 회원 조회·생성에 쓰는 카카오 사용자 정보.
 * auth 쪽 카카오 응답 DTO가 이 타입으로 변환해 넘긴다(member가 auth를 참조하지 않도록 member가 소유).
 *
 * @param email 동의하지 않았으면 null
 */
public record KakaoProfile(Long kakaoId, String nickname, String profileImageUrl, String email) {
}

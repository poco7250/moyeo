package com.poco7250.moyeo.og.dto;

/**
 * OG 메타 HTML에 넣는 값. redirectUrl은 링크를 연 사람을 보낼 SPA 주소다.
 */
public record OgMeta(String title, String description, String url, String redirectUrl) {
}

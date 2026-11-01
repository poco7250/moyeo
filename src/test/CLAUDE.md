# 테스트 작성 규칙

- 컨트롤러 `@WebMvcTest`는 `@Import(SecurityTestConfig.class)`(`src/test/.../support`)로 실제 시큐리티 체인을 올리고, 인증이 필요하면 `JwtTokenProvider`로 만든 토큰을 `access_token` 쿠키에 실어 보낸다(`MemberControllerTest` 참고).

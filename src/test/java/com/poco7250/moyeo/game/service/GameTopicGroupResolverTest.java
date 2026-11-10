package com.poco7250.moyeo.game.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.poco7250.moyeo.game.repository.GameSessionRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GameTopicGroupResolverTest {

	@Mock
	GameSessionRepository gameSessionRepository;

	@InjectMocks
	GameTopicGroupResolver resolver;

	@Test
	@DisplayName("게임 토픽만 처리한다")
	void supports() {
		assertThat(resolver.supports("/topic/games/1")).isTrue();
		assertThat(resolver.supports("/topic/meetings/1")).isFalse();
		assertThat(resolver.supports("/topic/gamesX")).isFalse();
	}

	@Test
	@DisplayName("게임 토픽을 게임이 속한 그룹 id로 해석한다")
	void resolvesGroupId() {
		given(gameSessionRepository.findGroupIdById(5L)).willReturn(Optional.of(3L));

		assertThat(resolver.resolveGroupId("/topic/games/5")).isEqualTo(3L);
	}

	@Test
	@DisplayName("없는 게임이면 null이다")
	void missingGame() {
		given(gameSessionRepository.findGroupIdById(404L)).willReturn(Optional.empty());

		assertThat(resolver.resolveGroupId("/topic/games/404")).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"/topic/games/", "/topic/games/abc", "/topic/games/1/extra",
			"/topic/games/-1", "/topic/games/0", "/topic/games/01",
			"/topic/games/99999999999999999999"})
	@DisplayName("형식이 틀린 게임 토픽은 조회 없이 null이다")
	void malformedDestination(String destination) {
		assertThat(resolver.resolveGroupId(destination)).isNull();
		verify(gameSessionRepository, never()).findGroupIdById(any());
	}
}

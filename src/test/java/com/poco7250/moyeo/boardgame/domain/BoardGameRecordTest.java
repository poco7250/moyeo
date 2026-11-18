package com.poco7250.moyeo.boardgame.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BoardGameRecordTest {

	private static final Long HOST_ID = 7L;
	private static final Long RECORDER_ID = 8L;

	private final Meeting meeting = Meeting.create(Group.create("대학 동기"), "연말 모임",
			LocalDateTime.of(2026, 12, 20, 18, 0), null, null, HOST_ID);
	private final Round round = Round.create(meeting, 1, "보드게임");

	@Test
	@DisplayName("순위는 순위 → 회원 id 순으로 정렬해 보관하고 게임명 앞뒤 공백을 지운다")
	void sortsRanks() {
		BoardGameRecord record = BoardGameRecord.create(round, "  스플렌더 ", RECORDER_ID,
				List.of(BoardGameRank.of(9L, 2), BoardGameRank.of(8L, 1), BoardGameRank.of(3L, 1)), 9L);

		assertThat(record.getGameName()).isEqualTo("스플렌더");
		assertThat(record.getRanks()).containsExactly(BoardGameRank.of(3L, 1), BoardGameRank.of(8L, 1),
				BoardGameRank.of(9L, 2));
	}

	@Test
	@DisplayName("수정하면 게임명·순위 목록·꼴등이 통째로 바뀐다")
	void updateReplaces() {
		BoardGameRecord record = BoardGameRecord.create(round, "스플렌더", RECORDER_ID,
				List.of(BoardGameRank.of(8L, 1), BoardGameRank.of(9L, 2)), 9L);

		record.update("카탄", List.of(BoardGameRank.of(10L, 2), BoardGameRank.of(9L, 1), BoardGameRank.of(8L, 3)),
				10L);

		assertThat(record.getGameName()).isEqualTo("카탄");
		assertThat(record.getLoserMemberId()).isEqualTo(10L);
		assertThat(record.getRanks()).containsExactly(BoardGameRank.of(9L, 1), BoardGameRank.of(10L, 2),
				BoardGameRank.of(8L, 3));
	}

	@Test
	@DisplayName("꼴등은 순위와 별개로 지정하지만 참가자가 아니면 만들거나 고칠 수 없다")
	void loserMustBeParticipant() {
		List<BoardGameRank> ranks = List.of(BoardGameRank.of(8L, 1), BoardGameRank.of(9L, 2));

		assertThat(BoardGameRecord.create(round, "스플렌더", RECORDER_ID, ranks, 8L).getLoserMemberId())
				.isEqualTo(8L);
		assertThatThrownBy(() -> BoardGameRecord.create(round, "스플렌더", RECORDER_ID, ranks, 10L))
				.isInstanceOf(IllegalArgumentException.class);
		BoardGameRecord record = BoardGameRecord.create(round, "스플렌더", RECORDER_ID, ranks, 9L);
		assertThatThrownBy(() -> record.update("카탄", ranks, 10L)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("기록한 사람과 모임 호스트만 고치거나 지울 수 있다")
	void editableBy() {
		BoardGameRecord record = BoardGameRecord.create(round, "스플렌더", RECORDER_ID,
				List.of(BoardGameRank.of(8L, 1), BoardGameRank.of(9L, 2)), 9L);

		assertThat(record.isEditableBy(RECORDER_ID, meeting)).isTrue();
		assertThat(record.isEditableBy(HOST_ID, meeting)).isTrue();
		assertThat(record.isEditableBy(9L, meeting)).isFalse();
	}
}

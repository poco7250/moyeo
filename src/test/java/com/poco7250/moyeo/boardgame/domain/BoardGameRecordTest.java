package com.poco7250.moyeo.boardgame.domain;

import static org.assertj.core.api.Assertions.assertThat;

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
				List.of(BoardGameRank.of(9L, 2), BoardGameRank.of(8L, 1), BoardGameRank.of(3L, 1)));

		assertThat(record.getGameName()).isEqualTo("스플렌더");
		assertThat(record.getRanks()).containsExactly(BoardGameRank.of(3L, 1), BoardGameRank.of(8L, 1),
				BoardGameRank.of(9L, 2));
	}

	@Test
	@DisplayName("수정하면 게임명과 순위 목록이 통째로 바뀐다")
	void updateReplaces() {
		BoardGameRecord record = BoardGameRecord.create(round, "스플렌더", RECORDER_ID,
				List.of(BoardGameRank.of(8L, 1), BoardGameRank.of(9L, 2)));

		record.update("카탄", List.of(BoardGameRank.of(10L, 2), BoardGameRank.of(9L, 1), BoardGameRank.of(8L, 3)));

		assertThat(record.getGameName()).isEqualTo("카탄");
		assertThat(record.getRanks()).containsExactly(BoardGameRank.of(9L, 1), BoardGameRank.of(10L, 2),
				BoardGameRank.of(8L, 3));
	}

	@Test
	@DisplayName("기록한 사람과 모임 호스트만 고치거나 지울 수 있다")
	void editableBy() {
		BoardGameRecord record = BoardGameRecord.create(round, "스플렌더", RECORDER_ID,
				List.of(BoardGameRank.of(8L, 1), BoardGameRank.of(9L, 2)));

		assertThat(record.isEditableBy(RECORDER_ID, meeting)).isTrue();
		assertThat(record.isEditableBy(HOST_ID, meeting)).isTrue();
		assertThat(record.isEditableBy(9L, meeting)).isFalse();
	}
}

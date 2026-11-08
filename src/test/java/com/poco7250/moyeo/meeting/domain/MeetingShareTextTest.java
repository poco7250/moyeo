package com.poco7250.moyeo.meeting.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.place.domain.Place;
import com.poco7250.moyeo.place.domain.PlaceCandidate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class MeetingShareTextTest {

	private static final LocalDateTime SATURDAY = LocalDateTime.of(2026, 12, 19, 18, 0);

	@Test
	void OG_설명에_요일과_1차_확정_장소와_참석_인원이_들어간다() {
		assertThat(MeetingShareText.ogDescription(SATURDAY, "강남 OO식당", 5, 8))
				.isEqualTo("토요일 모임 · 강남 OO식당 · 5/8명 참석");
	}

	@Test
	void 장소가_없으면_OG_설명에_장소_미정이라고_쓴다() {
		assertThat(MeetingShareText.ogDescription(SATURDAY, null, 0, 8))
				.isEqualTo("토요일 모임 · 장소 미정 · 0/8명 참석");
	}

	@Test
	void 카카오톡_문구에_제목_일시_장소_링크가_들어간다() {
		assertThat(MeetingShareText.kakaoText("연말 모임", SATURDAY, "강남 OO식당", "https://moyeo.example.com/share/meetings/t"))
				.isEqualTo("[연말 모임]\n12월 19일(토) 18:00\n장소: 강남 OO식당\nhttps://moyeo.example.com/share/meetings/t");
	}

	@Test
	void 카카오톡_문구도_장소가_없으면_장소_미정이다() {
		assertThat(MeetingShareText.kakaoText("연말 모임", SATURDAY, null, "/share/meetings/t")).contains("장소: 장소 미정");
	}

	@Test
	void 첫_차수의_확정_장소_이름을_찾고_미확정이거나_차수가_없으면_null이다() {
		Round first = round(1);
		Round second = round(2);
		second.confirmDirectly(PlaceCandidate.propose(second, Place.of("볼링장", null, null),
				Member.ofKakao(1L, "호스트", null, null)));

		assertThat(MeetingShareText.firstPlaceLabel(List.of(first, second))).isNull();
		assertThat(MeetingShareText.firstPlaceLabel(List.of(second))).isEqualTo("볼링장");
		assertThat(MeetingShareText.firstPlaceLabel(List.of())).isNull();
	}

	private Round round(int seq) {
		return Round.create(Meeting.create(Group.create("대학 동기"), "연말 모임", SATURDAY, null, null, 1L), seq, "차수");
	}
}

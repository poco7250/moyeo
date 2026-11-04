package com.poco7250.moyeo.place.service;

import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.place.domain.Place;
import com.poco7250.moyeo.place.domain.PlaceCandidate;
import java.time.LocalDateTime;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 장소 서비스 단위 테스트용 엔티티 생성 도우미(id를 직접 채운다).
 */
final class PlaceServiceFixture {

	static final Long MEETING_ID = 10L;
	static final Long ROUND_ID = 100L;
	static final Long HOST_ID = 7L;
	static final Long MEMBER_ID = 8L;

	private PlaceServiceFixture() {
	}

	/** id가 채워진 차수(장소 미정)를 만든다. */
	static Round round() {
		Meeting meeting = Meeting.create(Group.create("대학 동기"), "연말 모임",
				LocalDateTime.of(2026, 12, 20, 18, 0), null, null, HOST_ID);
		ReflectionTestUtils.setField(meeting, "id", MEETING_ID);
		Round round = Round.create(meeting, 1, "밥");
		ReflectionTestUtils.setField(round, "id", ROUND_ID);
		return round;
	}

	/** id가 채워진 회원을 만든다. */
	static Member member(Long id) {
		Member member = Member.ofKakao(id + 1000, "회원" + id, null, null);
		ReflectionTestUtils.setField(member, "id", id);
		return member;
	}

	/** 차수에 id가 채워진 후보를 만든다. */
	static PlaceCandidate candidate(Round round, Long id, String label) {
		PlaceCandidate candidate = PlaceCandidate.propose(round, Place.of(label, null, null), member(MEMBER_ID));
		ReflectionTestUtils.setField(candidate, "id", id);
		return candidate;
	}
}

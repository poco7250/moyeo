package com.poco7250.moyeo.place.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poco7250.moyeo.global.config.JpaAuditingConfig;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.place.domain.Place;
import com.poco7250.moyeo.place.domain.PlaceCandidate;
import com.poco7250.moyeo.place.domain.PlaceVote;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class PlaceVoteRepositoryTest {

	@Autowired
	PlaceVoteRepository placeVoteRepository;

	@Autowired
	PlaceCandidateRepository placeCandidateRepository;

	@Autowired
	RoundRepository roundRepository;

	@Autowired
	MeetingRepository meetingRepository;

	@Autowired
	GroupRepository groupRepository;

	@Autowired
	MemberRepository memberRepository;

	private Member member;
	private PlaceCandidate first;
	private PlaceCandidate second;

	@BeforeEach
	void setUp() {
		member = memberRepository.saveAndFlush(Member.ofKakao(1001L, "모여", null, null));
		Group group = groupRepository.saveAndFlush(Group.create("대학 동기"));
		Meeting meeting = meetingRepository.saveAndFlush(Meeting.create(
				group, "연말 모임", LocalDateTime.of(2026, 12, 20, 18, 0), null, null, member.getId()));
		Round round = roundRepository.saveAndFlush(Round.create(meeting, 1, "밥"));
		first = placeCandidateRepository.saveAndFlush(PlaceCandidate.propose(round, Place.of("고깃집", null, null), member));
		second = placeCandidateRepository.saveAndFlush(PlaceCandidate.propose(round, Place.of("횟집", null, null), member));
	}

	@Test
	@DisplayName("같은 후보에 같은 회원이 두 번 투표하면 유니크 제약 위반이다")
	void duplicateVote() {
		placeVoteRepository.saveAndFlush(PlaceVote.cast(first, member));

		assertThatThrownBy(() -> placeVoteRepository.saveAndFlush(PlaceVote.cast(first, member)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("같은 회원이라도 다른 후보에는 투표할 수 있다(복수 선택)")
	void voteOtherCandidate() {
		placeVoteRepository.saveAndFlush(PlaceVote.cast(first, member));
		placeVoteRepository.saveAndFlush(PlaceVote.cast(second, member));

		assertThat(placeVoteRepository.count()).isEqualTo(2);
	}
}

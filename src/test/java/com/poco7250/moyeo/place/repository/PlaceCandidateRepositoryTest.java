package com.poco7250.moyeo.place.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.poco7250.moyeo.global.config.JpaAuditingConfig;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.PlaceStatus;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.place.domain.Place;
import com.poco7250.moyeo.place.domain.PlaceCandidate;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class PlaceCandidateRepositoryTest {

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

	@Autowired
	EntityManager entityManager;

	private Member member;
	private Round round;

	@BeforeEach
	void setUp() {
		member = memberRepository.saveAndFlush(Member.ofKakao(1001L, "모여", null, null));
		Group group = groupRepository.saveAndFlush(Group.create("대학 동기"));
		Meeting meeting = meetingRepository.saveAndFlush(Meeting.create(
				group, "연말 모임", LocalDateTime.of(2026, 12, 20, 18, 0), null, null, member.getId()));
		round = roundRepository.saveAndFlush(Round.create(meeting, 1, "밥"));
	}

	/** 영속성 컨텍스트를 비우고 DB에서 다시 읽는다. */
	private PlaceCandidate reload(PlaceCandidate candidate) {
		entityManager.clear();
		return placeCandidateRepository.findById(candidate.getId()).orElseThrow();
	}

	@Test
	@DisplayName("이름만 있는 장소 후보는 주소·지도 링크가 null로 저장된다")
	void labelOnly() {
		PlaceCandidate saved = placeCandidateRepository.saveAndFlush(
				PlaceCandidate.propose(round, Place.of("고깃집", null, null), member));

		Place place = reload(saved).getPlace();
		assertThat(place.getLabel()).isEqualTo("고깃집");
		assertThat(place.getManualAddress()).isNull();
		assertThat(place.getManualMapUrl()).isNull();
	}

	@Test
	@DisplayName("주소·지도 링크까지 있는 장소 후보를 저장하고 제안자를 함께 읽는다")
	void fullPlace() {
		String mapUrl = "https://place.map.kakao.com/" + "1".repeat(2020);
		Place place = Place.of("강남 볼링장", "서울 강남구 강남대로 1", mapUrl);
		PlaceCandidate saved = placeCandidateRepository.saveAndFlush(PlaceCandidate.propose(round, place, member));

		PlaceCandidate found = reload(saved);
		assertThat(found.getPlace()).isEqualTo(place);
		assertThat(found.getProposedBy().getId()).isEqualTo(member.getId());
		assertThat(found.getRound().getId()).isEqualTo(round.getId());
	}

	@Test
	@DisplayName("바로 확정하면 차수에 확정 후보가 저장된다")
	void confirmedCandidate() {
		PlaceCandidate candidate = placeCandidateRepository.saveAndFlush(
				PlaceCandidate.propose(round, Place.of("고깃집", null, null), member));
		round.confirmDirectly(candidate);
		roundRepository.saveAndFlush(round);
		entityManager.clear();

		Round found = roundRepository.findById(round.getId()).orElseThrow();
		assertThat(found.getPlaceStatus()).isEqualTo(PlaceStatus.CONFIRMED);
		assertThat(found.getConfirmedCandidate().getId()).isEqualTo(candidate.getId());
	}

	@Test
	@DisplayName("place_candidate 테이블에는 사용자 입력 컬럼만 있고 카카오 응답 값 컬럼은 없다")
	@SuppressWarnings("unchecked")
	void noKakaoColumns() {
		List<String> columns = entityManager.createNativeQuery("""
				select lower(column_name) from information_schema.columns
				where table_schema = database() and table_name = 'place_candidate'
				""").getResultList();

		assertThat(columns).containsExactlyInAnyOrder(
				"id", "round_id", "proposed_by_id", "label", "manual_address", "manual_map_url",
				"created_at", "updated_at");
	}
}

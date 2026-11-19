package com.poco7250.moyeo.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.domain.GroupRole;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.place.domain.Place;
import com.poco7250.moyeo.place.domain.PlaceCandidate;
import com.poco7250.moyeo.place.domain.PlaceLinkFactory;
import com.poco7250.moyeo.place.dto.CandidateResponse;
import com.poco7250.moyeo.place.dto.FavoritePlaceResponse;
import com.poco7250.moyeo.place.dto.PlaceInput;
import com.poco7250.moyeo.place.dto.RecentPlaceResponse;
import com.poco7250.moyeo.place.repository.PlaceCandidateRepository;
import com.poco7250.moyeo.support.ConcurrentRunner;
import com.poco7250.moyeo.support.ConcurrentRunner.Outcome;
import com.poco7250.moyeo.support.DatabaseCleaner;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import java.time.LocalDateTime;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 그룹 단골 장소·지난 장소(PRD P-8) 통합 테스트. 동시 저장을 실제 커밋으로 보려고 트랜잭션 롤백 대신 매번 테이블을 비운다.
 * 지난 모임은 2019·2020년, 다가오는 모임은 2099년으로 둬 실제 시계와 상관없이 구분한다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class GroupPlaceIntegrationTest {

	private static final LocalDateTime MAY_2019 = LocalDateTime.of(2019, 5, 10, 18, 0);
	private static final LocalDateTime JUNE_2020 = LocalDateTime.of(2020, 6, 5, 18, 0);
	private static final LocalDateTime FUTURE = LocalDateTime.of(2099, 12, 20, 18, 0);

	private static long nextKakaoId = 980_000L;

	@Autowired
	GroupPlaceService groupPlaceService;

	@Autowired
	PlaceCandidateService placeCandidateService;

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	GroupRepository groupRepository;

	@Autowired
	GroupMemberRepository groupMemberRepository;

	@Autowired
	MeetingRepository meetingRepository;

	@Autowired
	RoundRepository roundRepository;

	@Autowired
	PlaceCandidateRepository placeCandidateRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	private Group group;
	private Group otherGroup;
	private Member host;
	private Member minsu;
	private Member outsider;

	@BeforeEach
	void setUp() {
		group = groupRepository.save(Group.create("대학 동기"));
		otherGroup = groupRepository.save(Group.create("회사 동료"));
		host = joinGroup(group, "호스트");
		minsu = joinGroup(group, "민수");
		outsider = joinGroup(otherGroup, "외부인");
	}

	@AfterEach
	void tearDown() {
		new DatabaseCleaner(jdbcTemplate).truncateAll();
	}

	@Test
	@DisplayName("단골을 저장하면 최근 저장 순으로 나오고, 저장하지 않은 멤버도 지울 수 있으며 지운 단골을 다시 지우면 404다")
	void favoriteLifecycle() {
		FavoritePlaceResponse meat = groupPlaceService.addFavorite(group.getId(), host.getId(),
				new PlaceInput("강남 고깃집", "서울 강남구", null));
		FavoritePlaceResponse bowling = groupPlaceService.addFavorite(group.getId(), minsu.getId(),
				new PlaceInput("볼링장", null, "https://place.map.kakao.com/1"));

		assertThat(groupPlaceService.listFavorites(group.getId(), minsu.getId()))
				.extracting(FavoritePlaceResponse::label, FavoritePlaceResponse::mapUrl,
						FavoritePlaceResponse::createdByMemberId)
				.containsExactly(
						tuple("볼링장", "https://place.map.kakao.com/1", minsu.getId()),
						tuple("강남 고깃집", PlaceLinkFactory.mapUrl(Place.of("강남 고깃집", "서울 강남구", null)), host.getId()));

		groupPlaceService.deleteFavorite(group.getId(), meat.id(), minsu.getId());

		assertThat(groupPlaceService.listFavorites(group.getId(), host.getId()))
				.extracting(FavoritePlaceResponse::id).containsExactly(bowling.id());
		assertRejected(() -> groupPlaceService.deleteFavorite(group.getId(), meat.id(), host.getId()),
				ErrorCode.FAVORITE_PLACE_NOT_FOUND);
	}

	@Test
	@DisplayName("다른 그룹의 단골은 내 그룹 경로로 지울 수 없고(404), 그룹 밖 회원은 조회·저장·삭제가 403이다")
	void groupBoundary() {
		FavoritePlaceResponse others = groupPlaceService.addFavorite(otherGroup.getId(), outsider.getId(),
				new PlaceInput("회사 앞 국밥", null, null));

		assertRejected(() -> groupPlaceService.deleteFavorite(group.getId(), others.id(), host.getId()),
				ErrorCode.FAVORITE_PLACE_NOT_FOUND);
		assertRejected(() -> groupPlaceService.listFavorites(group.getId(), outsider.getId()),
				ErrorCode.NOT_GROUP_MEMBER);
		assertRejected(() -> groupPlaceService.addFavorite(group.getId(), outsider.getId(),
				new PlaceInput("볼링장", null, null)), ErrorCode.NOT_GROUP_MEMBER);
		assertRejected(() -> groupPlaceService.deleteFavorite(otherGroup.getId(), others.id(), host.getId()),
				ErrorCode.NOT_GROUP_MEMBER);
		assertRejected(() -> groupPlaceService.listRecentPlaces(group.getId(), outsider.getId()),
				ErrorCode.NOT_GROUP_MEMBER);
		assertThat(groupPlaceService.listFavorites(otherGroup.getId(), outsider.getId())).hasSize(1);
	}

	@RepeatedTest(3)
	@DisplayName("같은 장소를 두 멤버가 동시에 저장해도 1건만 저장되고 늦은 쪽은 409 FAVORITE_PLACE_DUPLICATED다")
	void concurrentDuplicate() {
		PlaceInput input = new PlaceInput("강남 볼링장", null, null);
		List<Outcome<FavoritePlaceResponse>> outcomes = ConcurrentRunner.runTogether(List.of(
				() -> groupPlaceService.addFavorite(group.getId(), host.getId(), input),
				() -> groupPlaceService.addFavorite(group.getId(), minsu.getId(), input)));

		assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
		Throwable failure = outcomes.stream().filter(o -> !o.succeeded()).findFirst().orElseThrow().failure();
		assertThat(failure).isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.FAVORITE_PLACE_DUPLICATED);
		assertThat(jdbcTemplate.queryForObject("select count(*) from favorite_place", Integer.class)).isEqualTo(1);
	}

	@Test
	@DisplayName("지난 장소는 지난 모임의 확정 차수만 같은 장소끼리 합쳐 최근 순으로 주고, 재투표 중·다가오는·날짜 투표 중·다른 그룹 모임은 뺀다")
	void recentPlaces() {
		Meeting may = saveMeeting(group, MAY_2019);
		confirmedRound(may, 1, Place.of("강남 고깃집", null, null));
		confirmedRound(may, 2, Place.of("볼링장", "서울 강남구", null));
		Meeting june = saveMeeting(group, JUNE_2020);
		confirmedRound(june, 1, Place.of("강남 고깃집", null, null));
		reopenedRound(june, 2, Place.of("재투표한 장소", null, null));
		confirmedRound(saveMeeting(group, FUTURE), 1, Place.of("다가오는 모임 장소", null, null));
		Meeting dateVoting = meetingRepository.save(
				Meeting.createWithDateVote(group, "날짜 투표", null, null, host.getId()));
		confirmedRound(dateVoting, 1, Place.of("날짜 투표 중 장소", null, null));
		confirmedRound(saveMeeting(otherGroup, JUNE_2020), 1, Place.of("남의 장소", null, null));

		assertThat(groupPlaceService.listRecentPlaces(group.getId(), minsu.getId()))
				.extracting(RecentPlaceResponse::label, RecentPlaceResponse::manualAddress,
						RecentPlaceResponse::lastUsedAt, RecentPlaceResponse::useCount)
				.containsExactly(
						tuple("강남 고깃집", null, JUNE_2020, 2),
						tuple("볼링장", "서울 강남구", MAY_2019, 1));
	}

	@Test
	@DisplayName("지난 장소 값을 그대로 후보 추가 API에 넣으면 같은 장소 후보가 생긴다(불러오기)")
	void loadRecentPlaceAsCandidate() {
		confirmedRound(saveMeeting(group, MAY_2019), 1,
				Place.of("볼링장", "서울 강남구", "https://place.map.kakao.com/1"));
		Round voting = Round.create(saveMeeting(group, FUTURE), 1, "볼링");
		voting.openVoting(FUTURE.minusDays(1));
		voting = roundRepository.save(voting);
		RecentPlaceResponse recent = groupPlaceService.listRecentPlaces(group.getId(), minsu.getId()).get(0);

		CandidateResponse candidate = placeCandidateService.add(voting.getId(), minsu.getId(),
				new PlaceInput(recent.label(), recent.manualAddress(), recent.manualMapUrl()));

		assertThat(candidate)
				.extracting(CandidateResponse::label, CandidateResponse::manualAddress, CandidateResponse::mapUrl)
				.containsExactly("볼링장", "서울 강남구", "https://place.map.kakao.com/1");
	}

	private Member joinGroup(Group target, String nickname) {
		Member member = memberRepository.save(Member.ofKakao(nextKakaoId++, nickname, null, null));
		groupMemberRepository.save(GroupMember.join(target, member, nickname, GroupRole.MEMBER));
		return member;
	}

	private Meeting saveMeeting(Group target, LocalDateTime startAt) {
		return meetingRepository.save(Meeting.create(target, "모임", startAt, null, null, host.getId()));
	}

	private void confirmedRound(Meeting meeting, int seq, Place place) {
		Round round = roundRepository.save(Round.create(meeting, seq, seq + "차"));
		round.confirmDirectly(placeCandidateRepository.save(PlaceCandidate.propose(round, place, host)));
		roundRepository.save(round);
	}

	private void reopenedRound(Meeting meeting, int seq, Place place) {
		Round round = roundRepository.save(Round.create(meeting, seq, seq + "차"));
		round.confirmDirectly(placeCandidateRepository.save(PlaceCandidate.propose(round, place, host)));
		round.openVoting(FUTURE);
		roundRepository.save(round);
	}

	private void assertRejected(ThrowingCallable call, ErrorCode code) {
		assertThatThrownBy(call).isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(code);
	}
}

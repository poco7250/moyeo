package com.poco7250.moyeo.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.poco7250.moyeo.global.auth.GroupAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.place.domain.FavoritePlace;
import com.poco7250.moyeo.place.domain.Place;
import com.poco7250.moyeo.place.dto.ConfirmedPlaceRow;
import com.poco7250.moyeo.place.dto.PlaceInput;
import com.poco7250.moyeo.place.dto.RecentPlaceResponse;
import com.poco7250.moyeo.place.repository.FavoritePlaceRepository;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * GroupPlaceService 단위 테스트. 단골 저장의 개수·중복 검사와 지난 장소 합치기 규칙을 본다.
 */
@ExtendWith(MockitoExtension.class)
class GroupPlaceServiceTest {

	private static final Long GROUP_ID = 5L;
	private static final Long MEMBER_ID = 8L;
	private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 1, 18, 0);

	@Mock
	GroupAccessChecker groupAccessChecker;

	@Mock
	GroupRepository groupRepository;

	@Mock
	FavoritePlaceRepository favoritePlaceRepository;

	@Mock
	RoundRepository roundRepository;

	@Mock
	MemberRepository memberRepository;

	@Mock
	Clock clock;

	@InjectMocks
	GroupPlaceService groupPlaceService;

	private final Group group = Group.create("대학 동기");
	private final Member member = Member.ofKakao(1L, "민수", null, null);

	@Test
	@DisplayName("단골이 이미 50개면 409 FAVORITE_PLACE_LIMIT이고 저장하지 않는다")
	void limit() {
		List<FavoritePlace> full = IntStream.range(0, FavoritePlace.MAX_PER_GROUP)
				.mapToObj(i -> favorite(Place.of("장소" + i, null, null)))
				.toList();
		given(favoritePlaceRepository.findAllByGroupIdForCheck(GROUP_ID)).willReturn(full);

		assertRejected(new PlaceInput("새 장소", null, null), ErrorCode.FAVORITE_PLACE_LIMIT);
	}

	@Test
	@DisplayName("공백을 지운 값이 이미 있는 단골과 같으면 409 FAVORITE_PLACE_DUPLICATED이고 저장하지 않는다")
	void duplicate() {
		given(favoritePlaceRepository.findAllByGroupIdForCheck(GROUP_ID))
				.willReturn(new ArrayList<>(List.of(favorite(Place.of("강남 볼링장", "서울 강남구", null)))));

		assertRejected(new PlaceInput(" 강남 볼링장 ", " 서울 강남구", " "), ErrorCode.FAVORITE_PLACE_DUPLICATED);
	}

	@Test
	@DisplayName("이름이 같아도 주소나 지도 링크가 다르면 다른 장소로 보고 저장한다")
	void sameLabelDifferentAddress() {
		given(favoritePlaceRepository.findAllByGroupIdForCheck(GROUP_ID))
				.willReturn(List.of(favorite(Place.of("스타벅스", "강남역점", null))));
		lockGroup();
		given(memberRepository.getReferenceById(MEMBER_ID)).willReturn(member);
		given(favoritePlaceRepository.saveAndFlush(any())).willAnswer(invocation -> invocation.getArgument(0));

		assertThat(groupPlaceService.addFavorite(GROUP_ID, MEMBER_ID, new PlaceInput("스타벅스", "역삼역점", null))
				.manualAddress()).isEqualTo("역삼역점");
	}

	@Test
	@DisplayName("지난 장소는 같은 장소끼리 합쳐 처음 나온(가장 최근) 시각과 사용 횟수를 주고, 최대 20개로 자른다")
	void mergeSamePlaces() {
		List<ConfirmedPlaceRow> rows = new ArrayList<>();
		rows.add(new ConfirmedPlaceRow(Place.of("고깃집", null, null), AT));
		rows.add(new ConfirmedPlaceRow(Place.of("볼링장", null, null), AT));
		rows.add(new ConfirmedPlaceRow(Place.of("고깃집", null, null), AT.minusMonths(1)));
		rows.add(new ConfirmedPlaceRow(Place.of("고깃집", "강남", null), AT.minusMonths(2)));
		IntStream.range(0, 30).forEach(i -> rows.add(
				new ConfirmedPlaceRow(Place.of("옛 장소" + i, null, null), AT.minusYears(1).minusDays(i))));

		List<RecentPlaceResponse> result = GroupPlaceService.mergeSamePlaces(rows);

		assertThat(result).hasSize(GroupPlaceService.RECENT_PLACE_LIMIT);
		assertThat(result.subList(0, 4))
				.extracting(RecentPlaceResponse::label, RecentPlaceResponse::manualAddress,
						RecentPlaceResponse::lastUsedAt, RecentPlaceResponse::useCount)
				.containsExactly(
						tuple("고깃집", null, AT, 2),
						tuple("볼링장", null, AT, 1),
						tuple("고깃집", "강남", AT.minusMonths(2), 1),
						tuple("옛 장소0", null, AT.minusYears(1), 1));
	}

	private void assertRejected(PlaceInput input, ErrorCode code) {
		lockGroup();
		assertThatThrownBy(() -> groupPlaceService.addFavorite(GROUP_ID, MEMBER_ID, input))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(code);
		verify(favoritePlaceRepository, never()).saveAndFlush(any());
	}

	private void lockGroup() {
		given(groupRepository.findByIdForUpdate(GROUP_ID)).willReturn(Optional.of(group));
	}

	private FavoritePlace favorite(Place place) {
		return FavoritePlace.save(group, place, member);
	}
}

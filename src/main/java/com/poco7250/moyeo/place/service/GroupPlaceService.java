package com.poco7250.moyeo.place.service;

import com.poco7250.moyeo.global.auth.GroupAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.place.domain.FavoritePlace;
import com.poco7250.moyeo.place.domain.Place;
import com.poco7250.moyeo.place.dto.ConfirmedPlaceRow;
import com.poco7250.moyeo.place.dto.FavoritePlaceResponse;
import com.poco7250.moyeo.place.dto.PlaceInput;
import com.poco7250.moyeo.place.dto.RecentPlaceResponse;
import com.poco7250.moyeo.place.repository.FavoritePlaceRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 그룹 단골 장소 저장·삭제·조회와 지난 모임 장소 조회(PRD P-8).
 * 불러오기는 프론트가 목록 값을 기존 후보 추가·바로 확정 API에 넣어 하므로 여기엔 쓰기 경로를 두지 않는다.
 */
@Service
@RequiredArgsConstructor
public class GroupPlaceService {

	public static final int RECENT_PLACE_LIMIT = 20;

	private final GroupAccessChecker groupAccessChecker;
	private final GroupRepository groupRepository;
	private final FavoritePlaceRepository favoritePlaceRepository;
	private final RoundRepository roundRepository;
	private final MemberRepository memberRepository;
	private final Clock clock;

	/**
	 * 그룹 단골 장소를 최근에 저장한 순으로 돌려준다.
	 *
	 * @throws BusinessException 그룹 멤버가 아니면(없는 그룹 포함) NOT_GROUP_MEMBER
	 */
	@Transactional(readOnly = true)
	public List<FavoritePlaceResponse> listFavorites(Long groupId, Long memberId) {
		groupAccessChecker.requireMember(groupId, memberId);
		return favoritePlaceRepository.findAllByGroupIdOrderByIdDesc(groupId).stream()
				.map(FavoritePlaceResponse::from)
				.toList();
	}

	/**
	 * 사용자가 입력한 장소를 그룹 단골로 저장한다. 그룹 행을 잠가 동시 저장을 한 줄로 세우고, 기존 단골은 잠금 읽기로 읽어
	 * 먼저 커밋된 저장까지 보고 개수·중복을 검사한다.
	 *
	 * @throws BusinessException 비멤버 NOT_GROUP_MEMBER, 50개 초과 FAVORITE_PLACE_LIMIT, 같은 장소 있음 FAVORITE_PLACE_DUPLICATED
	 */
	@Transactional
	public FavoritePlaceResponse addFavorite(Long groupId, Long memberId, PlaceInput input) {
		groupAccessChecker.requireMember(groupId, memberId);
		Group group = groupRepository.findByIdForUpdate(groupId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_GROUP_MEMBER));
		Place place = input.toPlace();
		requireAddable(favoritePlaceRepository.findAllByGroupIdForCheck(groupId), place);
		FavoritePlace favorite = favoritePlaceRepository.saveAndFlush(
				FavoritePlace.save(group, place, memberRepository.getReferenceById(memberId)));
		return FavoritePlaceResponse.from(favorite);
	}

	/**
	 * 그룹 단골 장소를 지운다. 그룹 멤버 누구나 지울 수 있다.
	 *
	 * @throws BusinessException 비멤버 NOT_GROUP_MEMBER, 없거나 다른 그룹 단골이면 FAVORITE_PLACE_NOT_FOUND
	 */
	@Transactional
	public void deleteFavorite(Long groupId, Long favoriteId, Long memberId) {
		groupAccessChecker.requireMember(groupId, memberId);
		FavoritePlace favorite = favoritePlaceRepository.findByIdAndGroupId(favoriteId, groupId)
				.orElseThrow(() -> new BusinessException(ErrorCode.FAVORITE_PLACE_NOT_FOUND));
		favoritePlaceRepository.delete(favorite);
	}

	/**
	 * 지난 모임에서 확정된 장소를 같은 장소끼리 합쳐 최근 순으로 최대 20개 돌려준다.
	 *
	 * @throws BusinessException 그룹 멤버가 아니면(없는 그룹 포함) NOT_GROUP_MEMBER
	 */
	@Transactional(readOnly = true)
	public List<RecentPlaceResponse> listRecentPlaces(Long groupId, Long memberId) {
		groupAccessChecker.requireMember(groupId, memberId);
		List<ConfirmedPlaceRow> rows = roundRepository.findAllConfirmedPlacesOfPastMeetings(groupId,
				LocalDateTime.now(clock));
		return mergeSamePlaces(rows);
	}

	private void requireAddable(List<FavoritePlace> existing, Place place) {
		if (existing.size() >= FavoritePlace.MAX_PER_GROUP) {
			throw new BusinessException(ErrorCode.FAVORITE_PLACE_LIMIT);
		}
		if (existing.stream().anyMatch(favorite -> favorite.getPlace().equals(place))) {
			throw new BusinessException(ErrorCode.FAVORITE_PLACE_DUPLICATED);
		}
	}

	/**
	 * 최근 순으로 들어온 행을 장소 값으로 묶는다. 처음 본 행의 시각이 가장 최근 사용 시각이다.
	 */
	static List<RecentPlaceResponse> mergeSamePlaces(List<ConfirmedPlaceRow> rows) {
		Map<Place, LocalDateTime> lastUsed = new LinkedHashMap<>();
		Map<Place, Integer> counts = new LinkedHashMap<>();
		for (ConfirmedPlaceRow row : rows) {
			lastUsed.putIfAbsent(row.place(), row.startAt());
			counts.merge(row.place(), 1, Integer::sum);
		}
		return lastUsed.entrySet().stream()
				.limit(RECENT_PLACE_LIMIT)
				.map(entry -> RecentPlaceResponse.of(entry.getKey(), entry.getValue(), counts.get(entry.getKey())))
				.toList();
	}
}

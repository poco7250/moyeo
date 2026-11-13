package com.poco7250.moyeo.settlement.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poco7250.moyeo.game.domain.GameStatus;
import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.penalty.domain.PenaltyMoneyType;
import com.poco7250.moyeo.penalty.domain.PenaltyStatus;
import com.poco7250.moyeo.settlement.dto.InProgressGameResponse;
import com.poco7250.moyeo.settlement.dto.SettlementPenaltyResponse;
import com.poco7250.moyeo.settlement.dto.UnsetPenaltyGameResponse;
import com.poco7250.moyeo.auth.token.JwtTokenProvider;
import com.poco7250.moyeo.auth.token.TokenCookieFactory;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.settlement.calc.SettlementWarningType;
import com.poco7250.moyeo.settlement.domain.SettlementStatus;
import com.poco7250.moyeo.settlement.dto.NetBalanceChangeResponse;
import com.poco7250.moyeo.settlement.dto.RevisionChangeResponse;
import com.poco7250.moyeo.settlement.dto.RevisionPenaltyChargeResponse;
import com.poco7250.moyeo.settlement.dto.SettlementMemberResponse;
import com.poco7250.moyeo.settlement.dto.SettlementResponse;
import com.poco7250.moyeo.settlement.dto.SettlementRevisionResponse;
import com.poco7250.moyeo.settlement.dto.SettlementTransferResponse;
import com.poco7250.moyeo.settlement.dto.SettlementWarningResponse;
import com.poco7250.moyeo.settlement.service.SettlementConfirmService;
import com.poco7250.moyeo.settlement.service.SettlementHistoryService;
import com.poco7250.moyeo.settlement.service.SettlementQueryService;
import com.poco7250.moyeo.support.SecurityTestConfig;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * SettlementController 슬라이스 테스트. 실제 시큐리티 체인으로 쿠키 인증을 거치고, 서비스는 목이다.
 */
@WebMvcTest(controllers = SettlementController.class)
@Import(SecurityTestConfig.class)
class SettlementControllerTest {

	private static final SettlementPenaltyResponse PENALTY_APPLIED = new SettlementPenaltyResponse(80L, 9L,
			PenaltyStatus.APPLIED, new SettlementPenaltyResponse.Game(70L, GameType.LADDER, 2, "볼링장"),
			new SettlementPenaltyResponse.Rule(PenaltyMoneyType.RATIO, null, 30,
					new SettlementPenaltyResponse.TargetExpense(31L, 2, "볼링장", "볼링", 100_000L), 8L,
					LocalDateTime.of(2026, 12, 20, 21, 30)),
			30_000L, null, null);
	private static final SettlementPenaltyResponse PENALTY_ASSIGNED = new SettlementPenaltyResponse(81L, 8L,
			PenaltyStatus.ASSIGNED, new SettlementPenaltyResponse.Game(71L, GameType.DRAW, 2, "볼링장"),
			null, null, null, null);
	private static final SettlementResponse PREVIEW = new SettlementResponse(SettlementStatus.DRAFT, null, true,
			List.of(new SettlementMemberResponse(8L, 10_000L, 10_000L),
					new SettlementMemberResponse(9L, 10_000L, -10_000L)),
			List.of(new SettlementTransferResponse(9L, 8L, 10_000L)),
			List.of(new SettlementWarningResponse(SettlementWarningType.NO_SHARE_TARGET, 31L)),
			List.of(PENALTY_APPLIED, PENALTY_ASSIGNED), null);
	private static final SettlementResponse CONFIRMED = new SettlementResponse(SettlementStatus.CONFIRMED, 1, false,
			List.of(new SettlementMemberResponse(8L, 10_000L, 10_000L)),
			List.of(new SettlementTransferResponse(9L, 8L, 10_000L)),
			List.of(), List.of(), null);

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	SettlementQueryService settlementQueryService;

	@MockitoBean
	SettlementConfirmService settlementConfirmService;

	@MockitoBean
	SettlementHistoryService settlementHistoryService;

	@Test
	@DisplayName("DRAFT 조회는 기준 리비전 null·미리보기 표시와 계산값·경고를 준다")
	void getDraft() throws Exception {
		given(settlementQueryService.get(10L, 8L)).willReturn(PREVIEW);

		mockMvc.perform(get("/api/meetings/10/settlement").cookie(accessCookie()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.status").value("DRAFT"))
				.andExpect(jsonPath("$.data.basisRevisionNo").isEmpty())
				.andExpect(jsonPath("$.data.preview").value(true))
				.andExpect(jsonPath("$.data.members[1].memberId").value(9))
				.andExpect(jsonPath("$.data.members[1].shareAmount").value(10_000))
				.andExpect(jsonPath("$.data.members[1].netBalance").value(-10_000))
				.andExpect(jsonPath("$.data.transfers[0].fromMemberId").value(9))
				.andExpect(jsonPath("$.data.transfers[0].toMemberId").value(8))
				.andExpect(jsonPath("$.data.transfers[0].amount").value(10_000))
				.andExpect(jsonPath("$.data.warnings[0].type").value("NO_SHARE_TARGET"))
				.andExpect(jsonPath("$.data.warnings[0].expenseId").value(31))
				.andExpect(jsonPath("$.data.penalties[0].penaltyId").value(80))
				.andExpect(jsonPath("$.data.penalties[0].memberId").value(9))
				.andExpect(jsonPath("$.data.penalties[0].status").value("APPLIED"))
				.andExpect(jsonPath("$.data.penalties[0].game.gameId").value(70))
				.andExpect(jsonPath("$.data.penalties[0].game.type").value("LADDER"))
				.andExpect(jsonPath("$.data.penalties[0].game.roundSeq").value(2))
				.andExpect(jsonPath("$.data.penalties[0].rule.moneyType").value("RATIO"))
				.andExpect(jsonPath("$.data.penalties[0].rule.ratioPct").value(30))
				.andExpect(jsonPath("$.data.penalties[0].rule.targetExpense.expenseId").value(31))
				.andExpect(jsonPath("$.data.penalties[0].rule.targetExpense.memo").value("볼링"))
				.andExpect(jsonPath("$.data.penalties[0].rule.setBy").value(8))
				.andExpect(jsonPath("$.data.penalties[0].rule.setAt").value("2026-12-20T21:30:00"))
				.andExpect(jsonPath("$.data.penalties[0].chargeAmount").value(30_000))
				.andExpect(jsonPath("$.data.penalties[1].status").value("ASSIGNED"))
				.andExpect(jsonPath("$.data.penalties[1].rule").isEmpty())
				.andExpect(jsonPath("$.data.penalties[1].chargeAmount").isEmpty());
	}

	@Test
	@DisplayName("CONFIRMED 조회는 기준 리비전 1이고 미리보기가 아니다")
	void getConfirmed() throws Exception {
		given(settlementQueryService.get(10L, 8L)).willReturn(CONFIRMED);

		mockMvc.perform(get("/api/meetings/10/settlement").cookie(accessCookie()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("CONFIRMED"))
				.andExpect(jsonPath("$.data.basisRevisionNo").value(1))
				.andExpect(jsonPath("$.data.preview").value(false));
	}

	@Test
	@DisplayName("본문 없이 확정하면 경고 미확인으로 처리해 200을 준다")
	void confirmWithoutBody() throws Exception {
		given(settlementConfirmService.confirm(10L, 8L, false)).willReturn(CONFIRMED);

		mockMvc.perform(post("/api/meetings/10/settlement/confirm").cookie(accessCookie()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("CONFIRMED"))
				.andExpect(jsonPath("$.data.basisRevisionNo").value(1))
				.andExpect(jsonPath("$.data.revisionChange").isEmpty());
	}

	@Test
	@DisplayName("재확정하면 기준 리비전 2와 직전 리비전 대비 변동액·안내 문구를 revisionChange에 싣는다")
	void reconfirmShowsRevisionChange() throws Exception {
		SettlementResponse reconfirmed = new SettlementResponse(SettlementStatus.CONFIRMED, 2, false,
				List.of(new SettlementMemberResponse(8L, 15_000L, 15_000L),
						new SettlementMemberResponse(9L, 15_000L, -15_000L)),
				List.of(new SettlementTransferResponse(9L, 8L, 15_000L)),
				List.of(), List.of(),
				new RevisionChangeResponse(1, List.of(
						new NetBalanceChangeResponse(8L, 10_000L, 15_000L, 5_000L),
						new NetBalanceChangeResponse(9L, -10_000L, -15_000L, -5_000L)),
						RevisionChangeResponse.NOTICE, "정산이 수정됐어요(리비전 2): …"));
		given(settlementConfirmService.confirm(10L, 8L, false)).willReturn(reconfirmed);

		mockMvc.perform(post("/api/meetings/10/settlement/confirm").cookie(accessCookie()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.basisRevisionNo").value(2))
				.andExpect(jsonPath("$.data.revisionChange.previousRevisionNo").value(1))
				.andExpect(jsonPath("$.data.revisionChange.changes[1].memberId").value(9))
				.andExpect(jsonPath("$.data.revisionChange.changes[1].previousNetBalance").value(-10_000))
				.andExpect(jsonPath("$.data.revisionChange.changes[1].currentNetBalance").value(-15_000))
				.andExpect(jsonPath("$.data.revisionChange.changes[1].delta").value(-5_000))
				.andExpect(jsonPath("$.data.revisionChange.notice").value(RevisionChangeResponse.NOTICE))
				.andExpect(jsonPath("$.data.revisionChange.shareText").value("정산이 수정됐어요(리비전 2): …"));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"{}|false",
			"{\"acknowledgeWarnings\":null}|false",
			"{\"acknowledgeWarnings\":false}|false",
			"{\"acknowledgeWarnings\":true}|true"
	})
	@DisplayName("요청 본문의 경고 확인 값을 서비스에 넘긴다")
	void confirmPassesAcknowledgement(String body, boolean acknowledged) throws Exception {
		given(settlementConfirmService.confirm(10L, 8L, acknowledged)).willReturn(CONFIRMED);

		mockMvc.perform(post("/api/meetings/10/settlement/confirm").cookie(accessCookie())
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk());
	}

	@ParameterizedTest
	@EnumSource(value = ErrorCode.class, names = {
			"NOT_GROUP_MEMBER", "NOT_MEETING_HOST", "SETTLEMENT_ALREADY_CONFIRMED", "WARNING_UNCONFIRMED",
			"PENALTY_CONDITION_UNSET", "MONEY_GAME_IN_PROGRESS"})
	@DisplayName("확정 거부는 에러 코드의 상태와 코드로 응답한다")
	void confirmRejected(ErrorCode errorCode) throws Exception {
		given(settlementConfirmService.confirm(10L, 8L, false)).willThrow(new BusinessException(errorCode));

		mockMvc.perform(post("/api/meetings/10/settlement/confirm").cookie(accessCookie()))
				.andExpect(status().is(errorCode.getStatus().value()))
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value(errorCode.name()))
				.andExpect(jsonPath("$.error.details").doesNotExist());
	}

	@Test
	@DisplayName("조건 미설정으로 막히면 409와 함께 게임별 미설정 벌칙 목록을 error.details에 싣는다")
	void confirmRejectedWithUnsetPenalties() throws Exception {
		given(settlementConfirmService.confirm(10L, 8L, false)).willThrow(new BusinessException(
				ErrorCode.PENALTY_CONDITION_UNSET,
				List.of(new UnsetPenaltyGameResponse(70L, GameType.LADDER, 2, "볼링장", List.of(100L), List.of(2L)))));

		mockMvc.perform(post("/api/meetings/10/settlement/confirm").cookie(accessCookie()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("PENALTY_CONDITION_UNSET"))
				.andExpect(jsonPath("$.error.details[0].gameId").value(70))
				.andExpect(jsonPath("$.error.details[0].type").value("LADDER"))
				.andExpect(jsonPath("$.error.details[0].roundSeq").value(2))
				.andExpect(jsonPath("$.error.details[0].roundName").value("볼링장"))
				.andExpect(jsonPath("$.error.details[0].penaltyIds[0]").value(100))
				.andExpect(jsonPath("$.error.details[0].memberIds[0]").value(2));
	}

	@Test
	@DisplayName("진행 중 금액형 게임으로 막히면 409와 게임 목록을, 경고 미확인이면 경고 내용을 error.details에 싣는다")
	void confirmRejectedWithGamesAndWarnings() throws Exception {
		given(settlementConfirmService.confirm(10L, 8L, false)).willThrow(new BusinessException(
				ErrorCode.MONEY_GAME_IN_PROGRESS,
				List.of(new InProgressGameResponse(72L, GameType.LADDER, GameStatus.WAITING, 2, "볼링장", 70L))));
		given(settlementConfirmService.confirm(10L, 8L, true)).willThrow(new BusinessException(
				ErrorCode.WARNING_UNCONFIRMED,
				List.of(new SettlementWarningResponse(SettlementWarningType.PENALTY_EXCEEDS_TARGET, 31L))));

		mockMvc.perform(post("/api/meetings/10/settlement/confirm").cookie(accessCookie()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.details[0].gameId").value(72))
				.andExpect(jsonPath("$.error.details[0].status").value("WAITING"))
				.andExpect(jsonPath("$.error.details[0].rerunOfId").value(70));
		mockMvc.perform(post("/api/meetings/10/settlement/confirm").cookie(accessCookie())
						.contentType(MediaType.APPLICATION_JSON).content("{\"acknowledgeWarnings\":true}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("WARNING_UNCONFIRMED"))
				.andExpect(jsonPath("$.error.details[0].type").value("PENALTY_EXCEEDS_TARGET"))
				.andExpect(jsonPath("$.error.details[0].expenseId").value(31));
	}

	@Test
	@DisplayName("비로그인 요청은 401이고 서비스를 부르지 않는다")
	void unauthenticated() throws Exception {
		mockMvc.perform(get("/api/meetings/10/settlement")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/meetings/10/settlement/confirm")).andExpect(status().isUnauthorized());
		verifyNoInteractions(settlementQueryService, settlementConfirmService);
	}

	@Test
	@DisplayName("리비전 목록은 번호 순으로 확정자·시각·부담액·순잔액·변동액(리비전 1은 null)·송금 안내·벌칙 스냅샷을 준다")
	void revisions() throws Exception {
		LocalDateTime at = LocalDateTime.of(2026, 12, 20, 22, 0);
		given(settlementHistoryService.revisions(10L, 8L)).willReturn(List.of(
				new SettlementRevisionResponse(1, 8L, at, false,
						List.of(new SettlementMemberResponse(8L, 10_000L, 10_000L)),
						null,
						List.of(new SettlementTransferResponse(9L, 8L, 10_000L)),
						List.of(new RevisionPenaltyChargeResponse(80L, 9L, 30_000L, PenaltyMoneyType.RATIO, null, 30,
								31L, 100_000L, "볼링"))),
				new SettlementRevisionResponse(2, 8L, at.plusHours(1), true,
						List.of(new SettlementMemberResponse(8L, 5_000L, 15_000L)),
						List.of(new NetBalanceChangeResponse(8L, 10_000L, 15_000L, 5_000L)),
						List.of(), List.of())));

		mockMvc.perform(get("/api/meetings/10/settlement/revisions").cookie(accessCookie()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].revisionNo").value(1))
				.andExpect(jsonPath("$.data[0].confirmedBy").value(8))
				.andExpect(jsonPath("$.data[0].confirmedAt").value("2026-12-20T22:00:00"))
				.andExpect(jsonPath("$.data[0].changes").isEmpty())
				.andExpect(jsonPath("$.data[0].transfers[0].fromMemberId").value(9))
				.andExpect(jsonPath("$.data[0].penaltyCharges[0].memberId").value(9))
				.andExpect(jsonPath("$.data[0].penaltyCharges[0].ratioPct").value(30))
				.andExpect(jsonPath("$.data[0].penaltyCharges[0].targetExpenseMemo").value("볼링"))
				.andExpect(jsonPath("$.data[1].warningsAcknowledged").value(true))
				.andExpect(jsonPath("$.data[1].changes[0].delta").value(5_000));
	}

	@Test
	@DisplayName("리비전 목록을 그룹 멤버가 아닌 회원이 조회하면 403 NOT_GROUP_MEMBER")
	void revisionsRejectsNonMember() throws Exception {
		given(settlementHistoryService.revisions(10L, 8L))
				.willThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER));

		mockMvc.perform(get("/api/meetings/10/settlement/revisions").cookie(accessCookie()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("NOT_GROUP_MEMBER"));
	}

	private Cookie accessCookie() {
		return new Cookie(TokenCookieFactory.ACCESS_TOKEN_COOKIE, jwtTokenProvider.createAccessToken(8L));
	}
}

package com.poco7250.moyeo.settlement.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.penalty.domain.PenaltyMoneyType;
import com.poco7250.moyeo.penalty.domain.PenaltyStatus;
import com.poco7250.moyeo.settlement.dto.SettlementPenaltyResponse;
import com.poco7250.moyeo.auth.token.JwtTokenProvider;
import com.poco7250.moyeo.auth.token.TokenCookieFactory;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.settlement.calc.SettlementWarningType;
import com.poco7250.moyeo.settlement.domain.SettlementStatus;
import com.poco7250.moyeo.settlement.dto.SettlementMemberResponse;
import com.poco7250.moyeo.settlement.dto.SettlementResponse;
import com.poco7250.moyeo.settlement.dto.SettlementTransferResponse;
import com.poco7250.moyeo.settlement.dto.SettlementWarningResponse;
import com.poco7250.moyeo.settlement.service.SettlementConfirmService;
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
			List.of(PENALTY_APPLIED, PENALTY_ASSIGNED));
	private static final SettlementResponse CONFIRMED = new SettlementResponse(SettlementStatus.CONFIRMED, 1, false,
			List.of(new SettlementMemberResponse(8L, 10_000L, 10_000L)),
			List.of(new SettlementTransferResponse(9L, 8L, 10_000L)),
			List.of(), List.of());

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	SettlementQueryService settlementQueryService;

	@MockitoBean
	SettlementConfirmService settlementConfirmService;

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
				.andExpect(jsonPath("$.data.basisRevisionNo").value(1));
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
			"NOT_GROUP_MEMBER", "NOT_MEETING_HOST", "SETTLEMENT_ALREADY_CONFIRMED", "WARNING_UNCONFIRMED"})
	@DisplayName("확정 거부는 에러 코드의 상태와 코드로 응답한다")
	void confirmRejected(ErrorCode errorCode) throws Exception {
		given(settlementConfirmService.confirm(10L, 8L, false)).willThrow(new BusinessException(errorCode));

		mockMvc.perform(post("/api/meetings/10/settlement/confirm").cookie(accessCookie()))
				.andExpect(status().is(errorCode.getStatus().value()))
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value(errorCode.name()));
	}

	@Test
	@DisplayName("비로그인 요청은 401이고 서비스를 부르지 않는다")
	void unauthenticated() throws Exception {
		mockMvc.perform(get("/api/meetings/10/settlement")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/meetings/10/settlement/confirm")).andExpect(status().isUnauthorized());
		verifyNoInteractions(settlementQueryService, settlementConfirmService);
	}

	private Cookie accessCookie() {
		return new Cookie(TokenCookieFactory.ACCESS_TOKEN_COOKIE, jwtTokenProvider.createAccessToken(8L));
	}
}

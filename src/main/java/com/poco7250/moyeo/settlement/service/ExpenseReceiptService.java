package com.poco7250.moyeo.settlement.service;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.global.storage.ReceiptStorage;
import com.poco7250.moyeo.settlement.domain.Expense;
import com.poco7250.moyeo.settlement.domain.ExpenseReceipt;
import com.poco7250.moyeo.settlement.domain.ReceiptImageType;
import com.poco7250.moyeo.settlement.dto.ExpenseReceiptChangedResponse;
import com.poco7250.moyeo.settlement.dto.ExpenseReceiptResponse;
import com.poco7250.moyeo.settlement.repository.ExpenseReceiptRepository;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 지출 영수증 사진 올리기·교체·삭제·조회(PRD S-1 확장, 2026-11-15 결정). 그룹 멤버 누구나 쓸 수 있고 지출당 1장이다.
 * 금액에 영향이 없어 정산 직렬화·REOPENED·변경 로그 대상이 아니며, 모임 토픽 알림만 보낸다.
 * 동시 첫 업로드는 expense_id 유니크, 동시 교체는 version으로 늦은 쪽이 409가 되고 재시도하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseReceiptService {

	private final ExpenseLoader expenseLoader;
	private final ExpenseReceiptRepository expenseReceiptRepository;
	private final ReceiptStorage receiptStorage;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	/**
	 * 영수증 사진을 올린다. 이미 있으면 교체하고, 이전 파일은 커밋 뒤 지운다.
	 *
	 * @throws BusinessException 없는 지출이면 EXPENSE_NOT_FOUND, 그룹 멤버가 아니면 NOT_GROUP_MEMBER,
	 *         10MB를 넘으면 RECEIPT_TOO_LARGE, JPEG·PNG·WEBP가 아니면 RECEIPT_INVALID_TYPE
	 */
	@Transactional
	public ExpenseReceiptResponse upload(Long expenseId, Long memberId, MultipartFile file) {
		Expense expense = expenseLoader.loadForMember(expenseId, memberId);
		byte[] content = readContent(file);
		ReceiptImageType imageType = ReceiptImageType.detect(content);
		String key = imageType.newStorageKey();
		receiptStorage.store(key, content);
		eventPublisher.publishEvent(ReceiptFileCleanupEvent.onRollback(key));
		ExpenseReceipt receipt = saveOrReplace(expense, key, imageType, content.length, memberId);
		// 유니크·version 충돌을 이 메서드 안에서 터뜨려 롤백(새 파일 정리)이 확실히 일어나게 한다
		expenseReceiptRepository.flush();
		ExpenseReceiptResponse response = ExpenseReceiptResponse.from(receipt);
		publishChanged(expense, response);
		return response;
	}

	/**
	 * 영수증 사진을 지운다. 파일은 커밋 뒤 지운다.
	 *
	 * @throws BusinessException 없는 지출이면 EXPENSE_NOT_FOUND, 그룹 멤버가 아니면 NOT_GROUP_MEMBER,
	 *         사진이 없으면 RECEIPT_NOT_FOUND
	 */
	@Transactional
	public void delete(Long expenseId, Long memberId) {
		Expense expense = expenseLoader.loadForMember(expenseId, memberId);
		ExpenseReceipt receipt = findReceipt(expenseId);
		expenseReceiptRepository.delete(receipt);
		expenseReceiptRepository.flush();
		eventPublisher.publishEvent(ReceiptFileCleanupEvent.onCommit(receipt.getStorageKey()));
		publishChanged(expense, null);
	}

	/**
	 * 영수증 사진 파일을 읽는다.
	 *
	 * @throws BusinessException 없는 지출이면 EXPENSE_NOT_FOUND, 그룹 멤버가 아니면 NOT_GROUP_MEMBER,
	 *         사진이나 파일이 없으면 RECEIPT_NOT_FOUND
	 */
	@Transactional(readOnly = true)
	public ReceiptFile load(Long expenseId, Long memberId) {
		expenseLoader.loadForMember(expenseId, memberId);
		ExpenseReceipt receipt = findReceipt(expenseId);
		String key = receipt.getStorageKey();
		return receiptStorage.load(key)
				.map(resource -> new ReceiptFile(resource, receipt.getImageType().getContentType(),
						receipt.getSize(), key))
				.orElseThrow(() -> {
					log.warn("영수증 행은 있는데 파일이 없음: expenseId={}, key={}", expenseId, key);
					return new BusinessException(ErrorCode.RECEIPT_NOT_FOUND);
				});
	}

	/**
	 * 지출 삭제 트랜잭션에서 영수증 행을 먼저 지우고(FK), 파일은 커밋 뒤 지운다. 인가는 호출한 쪽이 이미 했다.
	 */
	@Transactional
	public void removeWithExpense(Long expenseId) {
		expenseReceiptRepository.findByExpenseId(expenseId).ifPresent(receipt -> {
			expenseReceiptRepository.delete(receipt);
			eventPublisher.publishEvent(ReceiptFileCleanupEvent.onCommit(receipt.getStorageKey()));
		});
	}

	/**
	 * 지출 1건의 영수증 정보를 돌려준다. 없으면 null이다.
	 */
	@Transactional(readOnly = true)
	public ExpenseReceiptResponse findResponse(Long expenseId) {
		return expenseReceiptRepository.findByExpenseId(expenseId).map(ExpenseReceiptResponse::from).orElse(null);
	}

	/**
	 * 여러 지출의 영수증 정보를 지출 id별로 돌려준다. 사진이 없는 지출은 맵에 없다.
	 */
	@Transactional(readOnly = true)
	public Map<Long, ExpenseReceiptResponse> findResponses(Collection<Long> expenseIds) {
		if (expenseIds.isEmpty()) {
			return Map.of();
		}
		return expenseReceiptRepository.findAllByExpenseIdIn(expenseIds).stream()
				.collect(Collectors.toMap(receipt -> receipt.getExpense().getId(), ExpenseReceiptResponse::from));
	}

	private byte[] readContent(MultipartFile file) {
		if (file.getSize() > ExpenseReceipt.MAX_SIZE_BYTES) {
			throw new BusinessException(ErrorCode.RECEIPT_TOO_LARGE);
		}
		try {
			return file.getBytes();
		} catch (IOException e) {
			log.warn("업로드 파일 읽기 실패", e);
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
	}

	private ExpenseReceipt saveOrReplace(Expense expense, String key, ReceiptImageType imageType, long size,
			Long memberId) {
		LocalDateTime now = LocalDateTime.now(clock);
		Optional<ExpenseReceipt> existing = expenseReceiptRepository.findByExpenseId(expense.getId());
		if (existing.isEmpty()) {
			return expenseReceiptRepository.save(ExpenseReceipt.create(expense, key, imageType, size, memberId, now));
		}
		String previousKey = existing.get().replace(key, imageType, size, memberId, now);
		eventPublisher.publishEvent(ReceiptFileCleanupEvent.onCommit(previousKey));
		return existing.get();
	}

	private ExpenseReceipt findReceipt(Long expenseId) {
		return expenseReceiptRepository.findByExpenseId(expenseId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RECEIPT_NOT_FOUND));
	}

	private void publishChanged(Expense expense, ExpenseReceiptResponse receipt) {
		Long meetingId = expense.getRound().getMeeting().getId();
		eventPublisher.publishEvent(new ExpenseReceiptChangedEvent(meetingId,
				new ExpenseReceiptChangedResponse(expense.getId(), receipt)));
	}
}

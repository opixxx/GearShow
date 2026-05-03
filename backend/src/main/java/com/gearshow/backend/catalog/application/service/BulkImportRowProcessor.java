package com.gearshow.backend.catalog.application.service;

import com.gearshow.backend.catalog.application.dto.BulkImportCatalogItemResult.BulkImportError;
import com.gearshow.backend.catalog.application.dto.CreateCatalogItemCommand;
import com.gearshow.backend.catalog.application.port.in.CreateCatalogItemUseCase;
import com.gearshow.backend.catalog.domain.exception.DuplicateModelCodeException;
import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

/**
 * 단건 처리기.
 *
 * <p>일괄 등록의 한 항목을 독립된 트랜잭션으로 처리한다.
 * 호출자({@link BulkImportCatalogItemService})와 별도 빈으로 분리되어야 Spring AOP 프록시가
 * {@code REQUIRES_NEW} 어노테이션을 가로챈다 (자기 호출 시 프록시 미적용).</p>
 *
 * <p>본 클래스는 어떠한 예외도 호출자로 전파하지 않으며, 모든 결과는 {@link RowOutcome} 으로 반환한다.
 * catch 절에서는 {@link TransactionAspectSupport#currentTransactionStatus()} 로 명시적으로
 * {@code setRollbackOnly()} 를 호출하여 부분 INSERT 가 commit 되지 않도록 보장한다.</p>
 *
 * <p><b>RuntimeException 광범위 catch 정책:</b> 본 처리기는 "한 건의 비정상 동작이 다른 건을 막지 않아야 한다"
 * 라는 부분 성공 보장 요구를 따르므로, NPE/ClassCastException 등 프로그래밍 버그성 예외도 의도적으로 흡수한다.
 * 대신 catch 블록은 ERROR 로그에 예외 클래스명을 명시(`exception=` 키)하여 운영 알림/필터링에서 즉시
 * 식별 가능하게 한다. DB 제약 race condition({@code DataIntegrityViolationException}) 의 별도 처리는
 * 후속 PR 에서 검토한다.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BulkImportRowProcessor {

    private final CreateCatalogItemUseCase createCatalogItemUseCase;

    /**
     * 단일 카탈로그 아이템 등록을 처리한다.
     *
     * <p>{@code REQUIRES_NEW} 로 매 호출마다 새 트랜잭션을 시작하여, 호출자 트랜잭션이 있더라도
     * 본 메서드의 commit/rollback 이 외부에 영향을 주지 않는다.</p>
     *
     * @param rowIndex 입력 리스트 내 0-based 인덱스
     * @param command  단건 등록 커맨드
     * @return 처리 결과 (CREATED / DUPLICATE / FAILED)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RowOutcome processRow(int rowIndex, CreateCatalogItemCommand command) {
        try {
            createCatalogItemUseCase.create(command);
            return RowOutcome.created();
        } catch (DuplicateModelCodeException e) {
            markRollback();
            log.info("[bulk-import] 중복 모델 코드 — rowIndex={}, modelCode={}",
                    rowIndex, command.modelCode());
            return RowOutcome.duplicate(toError(rowIndex, command, e.getCode(), e.getMessage()));
        } catch (CustomException e) {
            markRollback();
            log.warn("[bulk-import] 도메인 검증 실패 — rowIndex={}, code={}",
                    rowIndex, e.getCode());
            return RowOutcome.failed(toError(rowIndex, command, e.getCode(), e.getMessage()));
        } catch (RuntimeException e) {
            markRollback();
            log.error("[bulk-import] 예상치 못한 오류 — rowIndex={}, exception={}",
                    rowIndex, e.getClass().getName(), e);
            return RowOutcome.failed(toError(rowIndex, command,
                    ErrorCode.INTERNAL_ERROR.name(),
                    ErrorCode.INTERNAL_ERROR.getMessage()));
        }
    }

    private static void markRollback() {
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
    }

    private static BulkImportError toError(int rowIndex, CreateCatalogItemCommand command,
                                           String errorCode, String message) {
        return new BulkImportError(
                rowIndex,
                command.category(),
                command.brand(),
                command.modelCode(),
                errorCode,
                message
        );
    }
}

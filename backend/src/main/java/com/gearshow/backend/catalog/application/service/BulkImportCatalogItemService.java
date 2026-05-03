package com.gearshow.backend.catalog.application.service;

import com.gearshow.backend.catalog.application.dto.BulkImportCatalogItemCommand;
import com.gearshow.backend.catalog.application.dto.BulkImportCatalogItemResult;
import com.gearshow.backend.catalog.application.dto.BulkImportCatalogItemResult.BulkImportError;
import com.gearshow.backend.catalog.application.dto.CreateCatalogItemCommand;
import com.gearshow.backend.catalog.application.port.in.BulkImportCatalogItemUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 카탈로그 아이템 일괄 등록 유스케이스 구현체.
 *
 * <p>{@code @Transactional(propagation = NEVER)} 로 호출자 트랜잭션과의 합류를 명시적으로 차단한다.
 * 호출자가 실수로 트랜잭션을 들고 들어오면 즉시 {@code IllegalTransactionStateException} 으로 실패시켜
 * 트랜잭션 분리 의도를 런타임으로 강제한다. 건별 트랜잭션은 {@link BulkImportRowProcessor} 의
 * {@code REQUIRES_NEW} 에 위임한다.</p>
 */
@Service
@RequiredArgsConstructor
public class BulkImportCatalogItemService implements BulkImportCatalogItemUseCase {

    private final BulkImportRowProcessor rowProcessor;

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public BulkImportCatalogItemResult bulkImport(BulkImportCatalogItemCommand command) {
        List<CreateCatalogItemCommand> items = command.items();
        List<BulkImportError> errors = new ArrayList<>();
        int created = 0;
        int skippedDuplicate = 0;
        int failed = 0;

        for (int rowIndex = 0; rowIndex < items.size(); rowIndex++) {
            RowOutcome outcome = rowProcessor.processRow(rowIndex, items.get(rowIndex));

            switch (outcome.status()) {
                case CREATED -> created++;
                case DUPLICATE -> {
                    skippedDuplicate++;
                    errors.add(outcome.error());
                }
                case FAILED -> {
                    failed++;
                    errors.add(outcome.error());
                }
            }
        }

        return new BulkImportCatalogItemResult(created, skippedDuplicate, failed, errors);
    }
}

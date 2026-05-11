package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.showcase.application.dto.UpdateShowcaseCommand;
import com.gearshow.backend.showcase.application.port.out.ShowcasePort;
import com.gearshow.backend.showcase.domain.exception.NotFoundShowcaseException;
import com.gearshow.backend.showcase.domain.exception.NotOwnerShowcaseException;
import com.gearshow.backend.showcase.domain.model.Showcase;
import com.gearshow.backend.showcase.domain.vo.ConditionGrade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ADR-024: UpdateShowcaseService 단위 테스트.
 *
 * <p>회귀 보호: (1) 비소유자 거부, (2) 부분 update 가 도메인 update 후 저장, (3) Showcase 미존재 시 예외.
 * search_text 합성 인프라가 ADR-024 §D1 으로 폐기되어 재합성/synchronize 분기가 모두 사라졌다.</p>
 */
@ExtendWith(MockitoExtension.class)
class UpdateShowcaseServiceTest {

    @Mock
    private ShowcasePort showcasePort;

    @InjectMocks
    private UpdateShowcaseService service;

    @Test
    @DisplayName("title 변경이 도메인 update 를 거쳐 save 된다")
    void update_titleChange_savesUpdatedShowcase() {
        Showcase existing = newShowcase(1L, 100L);
        when(showcasePort.findById(100L)).thenReturn(Optional.of(existing));

        UpdateShowcaseCommand cmd = new UpdateShowcaseCommand(
                "새 제목", null, null, null, null, null, null);

        service.update(100L, 1L, cmd);

        ArgumentCaptor<Showcase> captor = ArgumentCaptor.forClass(Showcase.class);
        verify(showcasePort).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("새 제목");
    }

    @Test
    @DisplayName("userSize 같은 비검색 필드도 동일하게 save 1회")
    void update_userSizeChange_savesUpdatedShowcase() {
        Showcase existing = newShowcase(1L, 100L);
        when(showcasePort.findById(100L)).thenReturn(Optional.of(existing));

        UpdateShowcaseCommand cmd = new UpdateShowcaseCommand(
                null, null, null, "275", null, null, null);

        service.update(100L, 1L, cmd);

        ArgumentCaptor<Showcase> captor = ArgumentCaptor.forClass(Showcase.class);
        verify(showcasePort).save(captor.capture());
        assertThat(captor.getValue().getUserSize()).isEqualTo("275");
    }

    @Test
    @DisplayName("Showcase 미존재 시 NotFoundShowcaseException — save 호출 0")
    void update_notFound_throwsException() {
        when(showcasePort.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(999L, 1L, anyCommand()))
                .isInstanceOf(NotFoundShowcaseException.class);
        verify(showcasePort, never()).save(any(Showcase.class));
    }

    @Test
    @DisplayName("비소유자 update 시도 시 NotOwnerShowcaseException — save 호출 0")
    void update_notOwner_throwsException() {
        Showcase existing = newShowcase(1L, 100L);
        when(showcasePort.findById(100L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(100L, 999L, anyCommand()))
                .isInstanceOf(NotOwnerShowcaseException.class);
        verify(showcasePort, never()).save(any(Showcase.class));
    }

    private static Showcase newShowcase(Long ownerId, Long id) {
        return Showcase.builder()
                .id(id)
                .ownerId(ownerId)
                .catalogItemId(10L)
                .category(Category.BOOTS)
                .brand("Nike")
                .modelCode("DJ-1")
                .title("테스트 쇼케이스")
                .description("테스트 설명")
                .conditionGrade(ConditionGrade.A)
                .wearCount(5)
                .forSale(false)
                .build();
    }

    private static UpdateShowcaseCommand anyCommand() {
        return new UpdateShowcaseCommand("새", null, null, null, null, null, null);
    }
}

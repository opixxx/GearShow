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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ADR-018 §D4: UpdateShowcaseService 단위 테스트.
 *
 * <p>핵심 회귀 보호: (1) 비소유자 거부, (2) 직접 입력 토큰 변경 시에만 search_text 재합성,
 * (3) Showcase 미존재 시 예외, (4) 재합성이 SearchTextSynchronizer 1회만 호출.</p>
 */
@ExtendWith(MockitoExtension.class)
class UpdateShowcaseServiceTest {

    @Mock
    private ShowcasePort showcasePort;

    @Mock
    private SearchTextSynchronizer searchTextSynchronizer;

    @InjectMocks
    private UpdateShowcaseService service;

    @Test
    @DisplayName("ADR-018 §D4: title 변경 시 search_text 재합성 후 save")
    void update_titleChange_resyncsSearchText() {
        // Given
        Showcase existing = newShowcase(1L, 100L);
        when(showcasePort.findById(100L)).thenReturn(Optional.of(existing));
        when(searchTextSynchronizer.synchronize(any(Showcase.class)))
                .thenAnswer(inv -> ((Showcase) inv.getArgument(0)).changeSearchText("재합성된 텍스트"));

        UpdateShowcaseCommand cmd = new UpdateShowcaseCommand(
                "새 제목", null, null, null, null, null, null);

        // When
        service.update(100L, 1L, cmd);

        // Then — synchronize 1회 + save 1회
        verify(searchTextSynchronizer, times(1)).synchronize(any(Showcase.class));
        ArgumentCaptor<Showcase> captor = ArgumentCaptor.forClass(Showcase.class);
        verify(showcasePort).save(captor.capture());
        assertThat(captor.getValue().getSearchText()).isEqualTo("재합성된 텍스트");
        assertThat(captor.getValue().getTitle()).isEqualTo("새 제목");
    }

    @Test
    @DisplayName("ADR-018 §D4: description 변경 시 재합성")
    void update_descriptionChange_resyncsSearchText() {
        Showcase existing = newShowcase(1L, 100L);
        when(showcasePort.findById(100L)).thenReturn(Optional.of(existing));
        when(searchTextSynchronizer.synchronize(any(Showcase.class)))
                .thenAnswer(inv -> ((Showcase) inv.getArgument(0)).changeSearchText("새 합성"));

        service.update(100L, 1L, new UpdateShowcaseCommand(
                null, "새 설명", null, null, null, null, null));

        verify(searchTextSynchronizer, times(1)).synchronize(any(Showcase.class));
    }

    @Test
    @DisplayName("ADR-018 §D4: modelCode 변경 시 재합성")
    void update_modelCodeChange_resyncsSearchText() {
        Showcase existing = newShowcase(1L, 100L);
        when(showcasePort.findById(100L)).thenReturn(Optional.of(existing));
        when(searchTextSynchronizer.synchronize(any(Showcase.class)))
                .thenAnswer(inv -> ((Showcase) inv.getArgument(0)).changeSearchText("새 합성"));

        service.update(100L, 1L, new UpdateShowcaseCommand(
                null, null, "NEW-CODE", null, null, null, null));

        verify(searchTextSynchronizer, times(1)).synchronize(any(Showcase.class));
    }

    @Test
    @DisplayName("ADR-018 §D4: 직접 입력 토큰 미변경 (userSize/conditionGrade/wearCount/forSale) 시 재합성 X")
    void update_nonSearchAffectingFields_doesNotResync() {
        // Given — 기존 search_text 가 있는 Showcase
        Showcase existing = newShowcase(1L, 100L).changeSearchText("기존 검색 텍스트");
        when(showcasePort.findById(100L)).thenReturn(Optional.of(existing));

        // When — userSize 만 변경 (검색 영향 없음)
        UpdateShowcaseCommand cmd = new UpdateShowcaseCommand(
                null, null, null, "275", null, null, null);
        service.update(100L, 1L, cmd);

        // Then — synchronize 호출 0, 기존 search_text 보존
        verify(searchTextSynchronizer, never()).synchronize(any(Showcase.class));
        ArgumentCaptor<Showcase> captor = ArgumentCaptor.forClass(Showcase.class);
        verify(showcasePort).save(captor.capture());
        assertThat(captor.getValue().getSearchText()).isEqualTo("기존 검색 텍스트");
        assertThat(captor.getValue().getUserSize()).isEqualTo("275");
    }

    @Test
    @DisplayName("Showcase 미존재 시 NotFoundShowcaseException — synchronize/save 호출 0")
    void update_notFound_throwsException() {
        when(showcasePort.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(999L, 1L, anyCommand()))
                .isInstanceOf(NotFoundShowcaseException.class);
        verify(searchTextSynchronizer, never()).synchronize(any(Showcase.class));
        verify(showcasePort, never()).save(any(Showcase.class));
    }

    @Test
    @DisplayName("비소유자 update 시도 시 NotOwnerShowcaseException — synchronize/save 호출 0")
    void update_notOwner_throwsException() {
        Showcase existing = newShowcase(1L, 100L);  // ownerId=1
        when(showcasePort.findById(100L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(100L, 999L, anyCommand()))  // 다른 ownerId
                .isInstanceOf(NotOwnerShowcaseException.class);
        verify(searchTextSynchronizer, never()).synchronize(any(Showcase.class));
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

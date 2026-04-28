package com.gearshow.backend.showcase.application.dto;

import com.gearshow.backend.showcase.domain.model.ShowcaseComment;

import java.time.Instant;
import java.util.Objects;

/**
 * 댓글 조회 결과.
 *
 * <p>API 스펙(api-spec.md §7-1) 의 {@code author} 중첩 객체 형태를 따른다.
 * 작성자 닉네임/프로필 이미지는 {@link UserProfile} 을 통해 enrichment 된 값이며,
 * 탈퇴/삭제 사용자는 {@code Author.nickname}, {@code Author.profileImageUrl} 이 {@code null}.</p>
 */
public record CommentResult(
        Long showcaseCommentId,
        Author author,
        String content,
        Instant createdAt
) {

    /**
     * 댓글 작성자 공개 정보.
     *
     * @param userId          작성자 ID (탈퇴/삭제 후에도 보존)
     * @param nickname        작성자 닉네임 (탈퇴/삭제 시 null)
     * @param profileImageUrl 프로필 이미지 URL (미설정 또는 탈퇴/삭제 시 null)
     */
    public record Author(Long userId, String nickname, String profileImageUrl) {
    }

    /**
     * 도메인 모델 + 작성자 프로필 묶음으로부터 결과를 생성한다.
     *
     * <p>1-arg 팩토리는 의도적으로 제공하지 않는다 — 작성자 정보가 누락된 상태로
     * DTO 가 생성되는 것을 컴파일러가 막는다.</p>
     */
    public static CommentResult from(ShowcaseComment comment, UserProfile profile) {
        Objects.requireNonNull(comment, "comment");
        Objects.requireNonNull(profile, "profile — UserReadPort 가 placeholder 라도 반환하도록 보장된다");
        return new CommentResult(
                comment.getId(),
                new Author(profile.userId(), profile.nickname(), profile.profileImageUrl()),
                comment.getContent(),
                comment.getCreatedAt()
        );
    }
}

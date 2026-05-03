package com.gearshow.backend.catalog.domain.vo;

/**
 * 축구화 스터드 유형.
 *
 * <p>실제 시장 모델 (Mercurial Vapor MG, Predator HG 등) 을 모두 지원하기 위해
 * MG/HG 가 추가되었다 (ADR-016). 풋살화는 사용자 결정에 따라 {@link #TF} 로 매핑한다 (PR #71).</p>
 */
public enum StudType {

    /** Firm Ground - 천연잔디 */
    FG,

    /** Soft Ground - 부드러운 천연잔디 */
    SG,

    /** Artificial Ground - 인조잔디 */
    AG,

    /** Turf - 짧은 인조잔디 (풋살화 포함) */
    TF,

    /** Indoor Court - 실내 코트 */
    IC,

    /** Multi Ground - 혼합 잔디 (천연/인조 모두) */
    MG,

    /** Hard Ground - 단단한 천연잔디 (마른 그라운드) */
    HG
}

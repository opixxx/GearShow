package com.gearshow.backend.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Tag;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 헥사고날 아키텍처 경계 검증 테스트.
 *
 * <p>모든 Bounded Context에서 공통으로 지켜야 할 계층 간 의존 규칙을 결정론적으로 검증한다.
 * <code>architecture-reviewer</code> 서브에이전트가 의미 판단을 담당한다면, 이 테스트는
 * "명시적 규칙 위반"을 0원·수 초 내에 걸러낸다.</p>
 *
 * <p>실행 방법:</p>
 * <ul>
 *   <li><code>./gradlew archTest</code> — 아키텍처 테스트만 빠르게 실행 (pre-commit 훅에서 사용)</li>
 *   <li><code>./gradlew test</code> — 전체 테스트 실행 시에도 포함</li>
 * </ul>
 *
 * <p>규칙을 추가·변경할 때는 CLAUDE.md 또는 오케스트레이터 스킬의 정책 문서도 함께 갱신한다.</p>
 */
@Tag("architecture")
@AnalyzeClasses(
        packages = "com.gearshow.backend",
        importOptions = {ImportOption.DoNotIncludeTests.class}
)
class HexagonalArchitectureTest {

    /**
     * 규칙 1 — domain 계층은 Spring 프레임워크 의존 금지.
     * <p>순수 도메인을 유지하여 프레임워크 교체·테스트 용이성·표현력을 확보한다.
     * Spring 의존이 필요하면 application 또는 adapter 계층에 위치해야 한다.</p>
     */
    @ArchTest
    static final ArchRule domain_계층은_Spring_의존_금지 =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..",
                            "org.springframework.stereotype..",
                            "org.springframework.beans..",
                            "org.springframework.context.."
                    )
                    .because("domain 계층은 순수 비즈니스 로직만 담아야 하며 Spring 의존은 application/adapter 계층으로 위임한다");

    /**
     * 규칙 2 — domain 계층은 JPA 의존 금지.
     * <p>도메인 객체가 JPA 어노테이션에 오염되면 영속성 기술 변경이 불가능해지고
     * 테스트 시 컨테이너 기동이 필요해진다. <code>JpaEntity</code>는 adapter/out/persistence에 둔다.</p>
     */
    @ArchTest
    static final ArchRule domain_계층은_JPA_의존_금지 =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "jakarta.persistence..",
                            "javax.persistence..",
                            "org.hibernate.."
                    )
                    .because("JPA 의존은 adapter/out/persistence 계층의 책임이며 도메인 모델은 영속성 기술에 중립적이어야 한다");

    /**
     * 규칙 3 — domain 계층은 adapter 계층 의존 금지.
     * <p>의존 방향은 adapter → application → domain 순서로만 흘러야 한다.
     * domain이 adapter를 알면 외부 기술이 비즈니스 로직을 오염시킨다.</p>
     */
    @ArchTest
    static final ArchRule domain_계층은_adapter_의존_금지 =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("..adapter..")
                    .because("의존 방향은 adapter → application → domain 으로만 흘러야 한다");

    /**
     * 규칙 4 — application 계층은 adapter 계층 의존 금지.
     * <p>application은 port 인터페이스를 정의하고 adapter가 이를 구현한다(DIP).
     * application이 adapter를 직접 참조하면 의존성 역전이 깨지고 테스트 시 Mock 주입이 어려워진다.</p>
     */
    @ArchTest
    static final ArchRule application_계층은_adapter_의존_금지 =
            noClasses()
                    .that().resideInAPackage("..application..")
                    .should().dependOnClassesThat().resideInAPackage("..adapter..")
                    .because("application은 port 인터페이스만 정의하고 adapter가 이를 구현한다(DIP)");
}

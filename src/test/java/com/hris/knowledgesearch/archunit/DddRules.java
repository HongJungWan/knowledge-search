package com.hris.knowledgesearch.archunit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.hris.knowledgesearch.shared.ddd.AggregateInternal;
import com.hris.knowledgesearch.shared.ddd.AggregateRoot;
import com.hris.knowledgesearch.shared.ddd.ValueObject;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.GeneralCodingRules;

/**
 * cross-file 구조 규칙을 *정밀* 강제(훅의 휴리스틱과 달리 전체 클래스 그래프 분석). 드롭인용.
 * 실제 프로젝트 적용 시 각 규칙을 {@code FreezingArchRule.freeze(...)} 로 감싸 baseline 래칫 권장(ARCHUNIT.md).
 */
public final class DddRules {
    private DddRules() {}

    /** #3 도메인 순수성: 도메인 → application/infrastructure/presentation 의존 금지. */
    public static final ArchRule DOMAIN_PURITY = noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..application..", "..infrastructure..", "..infra..", "..adapter..", "..presentation..")
            .as("[DDD_DOMAIN_PURITY] 도메인은 바깥 레이어에 의존하지 않는다").allowEmptyShould(true);

    /** #4 DIP: 리포지토리 구현(*RepositoryImpl)은 infrastructure 에. */
    public static final ArchRule REPOSITORY_IMPL_IN_INFRA = classes().that()
            .haveSimpleNameEndingWith("RepositoryImpl")
            .should().resideInAnyPackage("..infrastructure..", "..infra..", "..adapter..")
            .as("[DDD_DIP] 리포지토리 구현체는 infrastructure 에 위치").allowEmptyShould(true);

    /** #9/#12 애그리거트 경계: @AggregateInternal 은 같은 패키지(애그리거트) 안에서만 접근. */
    public static final ArchRule AGGREGATE_ACCESS = classes().that()
            .areAnnotatedWith(AggregateInternal.class)
            .should(onlyBeAccessedWithinSameAggregate())
            .as("[DDD_AGGREGATE_ACCESS] 내부 엔티티는 애그리거트 루트를 통해서만 접근").allowEmptyShould(true);

    /** #13 ID 참조: 애그리거트 루트 필드가 다른 애그리거트 루트를 객체로 직접 참조 금지. */
    public static final ArchRule ID_REFERENCE_BETWEEN_AGGREGATES = fields().that()
            .areDeclaredInClassesThat().areAnnotatedWith(AggregateRoot.class)
            .should(notDirectlyReferenceAnotherAggregateRoot())
            .as("[DDD_ID_REFERENCE] 애그리거트 간 참조는 식별자(ID)로").allowEmptyShould(true);

    /** #16 VO 불변성: @ValueObject 의 필드는 final. */
    public static final ArchRule VALUE_OBJECT_IMMUTABLE = fields().that()
            .areDeclaredInClassesThat().areAnnotatedWith(ValueObject.class)
            .should().beFinal()
            .as("[DDD_VO_IMMUTABLE] 값 객체는 불변(final 필드)").allowEmptyShould(true);

    /** 필드 주입 금지(생성자 주입). */
    public static final ArchRule NO_FIELD_INJECTION = GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION
            .as("[DDD_NO_FIELD_INJECTION] 필드 주입 금지");

    private static ArchCondition<JavaClass> onlyBeAccessedWithinSameAggregate() {
        return new ArchCondition<>("only be accessed within the same aggregate (package)") {
            @Override
            public void check(JavaClass internal, ConditionEvents events) {
                for (Dependency dep : internal.getDirectDependenciesToSelf()) {
                    JavaClass origin = dep.getOriginClass().getBaseComponentType();
                    if (!origin.equals(internal) && !origin.getPackageName().equals(internal.getPackageName())) {
                        events.add(SimpleConditionEvent.violated(dep,
                                origin.getName() + " reaches into aggregate-internal " + internal.getSimpleName()
                                        + " from outside its aggregate"));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaField> notDirectlyReferenceAnotherAggregateRoot() {
        return new ArchCondition<>("not directly reference another aggregate root") {
            @Override
            public void check(JavaField field, ConditionEvents events) {
                JavaClass type = field.getRawType();
                if (type.isAnnotatedWith(AggregateRoot.class) && !type.equals(field.getOwner())) {
                    events.add(SimpleConditionEvent.violated(field,
                            field.getFullName() + " directly references aggregate root "
                                    + type.getSimpleName() + " (use its ID instead)"));
                }
            }
        };
    }
}

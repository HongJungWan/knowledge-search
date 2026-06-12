package com.hris.knowledgesearch.archunit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.hris.knowledgesearch.shared.ddd.AggregateInternal;
import com.hris.knowledgesearch.shared.ddd.AggregateRoot;
import com.hris.knowledgesearch.shared.ddd.Subdomain;
import com.hris.knowledgesearch.shared.ddd.SubdomainType;
import com.hris.knowledgesearch.shared.ddd.ValueObject;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.GeneralCodingRules;
import jakarta.persistence.Entity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * cross-file 구조 규칙을 *정밀* 강제(훅의 휴리스틱과 달리 전체 클래스 그래프 분석).
 */
public final class DddRules {
    private DddRules() {}

    /** #3 도메인 순수성: 도메인 → application/infrastructure/presentation 의존 금지. */
    public static final ArchRule DOMAIN_PURITY = noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..application..", "..infrastructure..", "..infra..", "..adapter..", "..presentation..")
            .as("[DDD_DOMAIN_PURITY] 도메인은 바깥 레이어에 의존하지 않는다").allowEmptyShould(false);

    /**
     * 레이어 경계: application 은 infrastructure 에 의존하지 않는다(포트 사용 — DIP).
     * 훅(휴리스틱)만 막던 경계를 CI 권위 게이트로도 강제한다.
     */
    public static final ArchRule APPLICATION_NOT_DEPEND_ON_INFRASTRUCTURE = noClasses().that()
            .resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..", "..infra..")
            .as("[DDD_APP_NOT_DEPEND_ON_INFRA] application 은 infrastructure 에 의존하지 않는다(포트 사용)")
            .allowEmptyShould(false);

    /** #4 DIP: 리포지토리 구현(*RepositoryImpl)은 infrastructure 에. */
    public static final ArchRule REPOSITORY_IMPL_IN_INFRA = classes().that()
            .haveSimpleNameEndingWith("RepositoryImpl")
            .should().resideInAnyPackage("..infrastructure..", "..infra..", "..adapter..")
            .as("[DDD_DIP] 리포지토리 구현체는 infrastructure 에 위치").allowEmptyShould(false);

    /** #9/#12 애그리거트 경계: @AggregateInternal 은 같은 패키지(애그리거트) 안에서만 접근. */
    public static final ArchRule AGGREGATE_ACCESS = classes().that()
            .areAnnotatedWith(AggregateInternal.class)
            .should(onlyBeAccessedWithinSameAggregate())
            // @AggregateInternal 은 현재 이 프로젝트에 미사용(매칭 클래스 0). 본 규칙만 vacuous 허용 유지.
            .as("[DDD_AGGREGATE_ACCESS] 내부 엔티티는 애그리거트 루트를 통해서만 접근").allowEmptyShould(true);

    /** #13 ID 참조: 애그리거트 루트 필드가 다른 애그리거트 루트를 객체로 직접 참조 금지. */
    public static final ArchRule ID_REFERENCE_BETWEEN_AGGREGATES = fields().that()
            .areDeclaredInClassesThat().areAnnotatedWith(AggregateRoot.class)
            .should(notDirectlyReferenceAnotherAggregateRoot())
            .as("[DDD_ID_REFERENCE] 애그리거트 간 참조는 식별자(ID)로").allowEmptyShould(false);

    /** #16 VO 불변성: @ValueObject 의 필드는 final. */
    public static final ArchRule VALUE_OBJECT_IMMUTABLE = fields().that()
            .areDeclaredInClassesThat().areAnnotatedWith(ValueObject.class)
            .should().beFinal()
            .as("[DDD_VO_IMMUTABLE] 값 객체는 불변(final 필드)").allowEmptyShould(false);

    /** 필드 주입 금지(생성자 주입). */
    public static final ArchRule NO_FIELD_INJECTION = GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION
            .as("[DDD_NO_FIELD_INJECTION] 필드 주입 금지");

    /** 마커 커버리지: 도메인 @Entity 는 @AggregateRoot 또는 @AggregateInternal 로 표시되어야 한다. */
    public static final ArchRule DOMAIN_ENTITY_MARKED = classes().that()
            .resideInAPackage("..domain..").and().areAnnotatedWith(Entity.class)
            .should().beAnnotatedWith(AggregateRoot.class)
            .orShould().beAnnotatedWith(AggregateInternal.class)
            .as("[DDD_DOMAIN_ENTITY_MARKED] 도메인 @Entity 는 @AggregateRoot/@AggregateInternal 로 표시")
            .allowEmptyShould(false);

    /** 애그리거트 루트는 자기 타입을 반환하는 public static 팩토리 메서드를 가져야 한다. */
    public static final ArchRule AGGREGATE_ROOT_HAS_FACTORY = classes().that()
            .areAnnotatedWith(AggregateRoot.class)
            .should(haveSelfReturningStaticFactory())
            .as("[DDD_AGGREGATE_ROOT_HAS_FACTORY] 애그리거트 루트는 정적 팩토리 메서드 보유")
            .allowEmptyShould(false);

    /**
     * 서브도메인 의존 방향: CORE 는 GENERIC 에 의존하지 않는다(핵심이 범용에 종속되지 않게).
     * ks 에는 현재 GENERIC 타입이 없어 vacuous 통과(allowEmptyShould(true)).
     */
    public static final ArchRule CORE_NOT_DEPEND_ON_GENERIC = classes().that(isSubdomain(SubdomainType.CORE))
            .should(notDependOnGenericSubdomain())
            .as("[DDD_CORE_NOT_DEPEND_ON_GENERIC] CORE 서브도메인은 GENERIC 서브도메인에 의존하지 않는다")
            .allowEmptyShould(true);

    /**
     * 요청 입력은 커맨드: @RestController 의 @RequestBody 파라미터 타입은 ..command.. 패키지이거나 *Command 명명.
     * (요청 DTO 대신 의도를 표현하는 커맨드 객체를 입력으로 받는다.)
     */
    public static final ArchRule REQUEST_INPUT_IS_COMMAND = classes().that()
            .areAnnotatedWith(RestController.class)
            .should(haveRequestBodyParamsAsCommand())
            .as("[DDD_REQUEST_INPUT_IS_COMMAND] @RequestBody 입력은 ..command.. 의 *Command 타입")
            // @RestController 가 실재하므로 vacuous 통과를 허용하지 않는다(어노테이션 누락 시 침묵 방지).
            .allowEmptyShould(false);

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

    private static ArchCondition<JavaClass> haveSelfReturningStaticFactory() {
        return new ArchCondition<>("have a public static factory method returning its own type") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                boolean hasFactory = false;
                for (JavaMethod method : clazz.getMethods()) {
                    if (method.getModifiers().contains(JavaModifier.PUBLIC)
                            && method.getModifiers().contains(JavaModifier.STATIC)
                            && method.getRawReturnType().equals(clazz)) {
                        hasFactory = true;
                        break;
                    }
                }
                if (!hasFactory) {
                    events.add(SimpleConditionEvent.violated(clazz,
                            clazz.getName() + " has no public static factory method returning "
                                    + clazz.getSimpleName()));
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

    /** @Subdomain(value) 가 주어진 유형인 클래스 술어. */
    private static DescribedPredicate<JavaClass> isSubdomain(SubdomainType type) {
        return new DescribedPredicate<>("@Subdomain(" + type + ")") {
            @Override
            public boolean test(JavaClass clazz) {
                return clazz.isAnnotatedWith(Subdomain.class)
                        && clazz.getAnnotationOfType(Subdomain.class).value() == type;
            }
        };
    }

    private static boolean isGenericSubdomain(JavaClass clazz) {
        return isSubdomain(SubdomainType.GENERIC).test(clazz);
    }

    private static ArchCondition<JavaClass> notDependOnGenericSubdomain() {
        return new ArchCondition<>("not depend on @Subdomain(GENERIC) classes") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                for (Dependency dep : clazz.getDirectDependenciesFromSelf()) {
                    JavaClass target = dep.getTargetClass().getBaseComponentType();
                    if (!target.equals(clazz) && isGenericSubdomain(target)) {
                        events.add(SimpleConditionEvent.violated(dep,
                                clazz.getName() + " (CORE) depends on GENERIC subdomain "
                                        + target.getSimpleName()));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> haveRequestBodyParamsAsCommand() {
        return new ArchCondition<>("have @RequestBody parameters typed as ..command.. *Command") {
            @Override
            public void check(JavaClass controller, ConditionEvents events) {
                for (JavaMethod method : controller.getMethods()) {
                    for (JavaParameter param : method.getParameters()) {
                        if (!param.isAnnotatedWith(RequestBody.class)) {
                            continue;
                        }
                        JavaClass type = param.getRawType().getBaseComponentType();
                        boolean ok = type.getPackageName().contains(".command")
                                || type.getSimpleName().endsWith("Command");
                        if (!ok) {
                            events.add(SimpleConditionEvent.violated(method,
                                    method.getFullName() + " @RequestBody parameter '" + type.getSimpleName()
                                            + "' is not a ..command.. *Command type"));
                        }
                    }
                }
            }
        };
    }
}

package com.hris.knowledgesearch.archunit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMembers;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.hris.knowledgesearch.shared.ddd.AggregateInternal;
import com.hris.knowledgesearch.shared.ddd.AggregateRoot;
import com.hris.knowledgesearch.shared.ddd.DomainService;
import com.hris.knowledgesearch.shared.ddd.Subdomain;
import com.hris.knowledgesearch.shared.ddd.SubdomainType;
import com.hris.knowledgesearch.shared.ddd.ValueObject;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.GeneralCodingRules;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * cross-file 구조 규칙을 *정밀* 강제(훅의 휴리스틱과 달리 전체 클래스 그래프 분석).
 * opinionated-harness-template 18규칙 + 본 레포 자체 2규칙(APPLICATION_NOT_DEPEND_ON_INFRASTRUCTURE,
 * DOMAIN_ENTITY_NO_RAW_STRING) = 20규칙. 매칭 타입이 실재하는 규칙은 allowEmptyShould(false)로 침묵 통과를 막는다.
 * 베이스라인 래칫이 필요하면 각 규칙을 FreezingArchRule.freeze(...) 로 감싼다(docs/ARCHUNIT.md).
 */
public final class DddRules {
    private DddRules() {}

    /** #3 도메인 순수성: 도메인 → application/infrastructure/presentation 의존 금지. */
    public static final ArchRule DOMAIN_PURITY = noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..application..", "..infrastructure..", "..infra..", "..adapter..", "..presentation..")
            .as("[DDD_DOMAIN_PURITY] 도메인은 바깥 레이어에 의존하지 않는다").allowEmptyShould(false);

    /** #4 DIP: 리포지토리 구현(*RepositoryImpl)은 infrastructure 에. */
    public static final ArchRule REPOSITORY_IMPL_IN_INFRA = classes().that()
            .haveSimpleNameEndingWith("RepositoryImpl")
            .should().resideInAnyPackage("..infrastructure..", "..infra..", "..adapter..")
            .as("[DDD_DIP] 리포지토리 구현체는 infrastructure 에 위치").allowEmptyShould(false);

    /** #9/#12 애그리거트 경계: @AggregateInternal 은 같은 패키지(애그리거트) 안에서만 접근. */
    public static final ArchRule AGGREGATE_ACCESS = classes().that()
            .areAnnotatedWith(AggregateInternal.class)
            .should(onlyBeAccessedWithinSameAggregate())
            // @AggregateInternal 현재 미사용(매칭 0) — 본 규칙만 vacuous 허용.
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

    /** 도메인 @Entity 는 @AggregateRoot/@AggregateInternal 로 표시(전술적 분류). 문자열로 매칭(jakarta 비의존). */
    public static final ArchRule DOMAIN_ENTITY_MARKED = classes().that()
            .resideInAPackage("..domain..").and().areAnnotatedWith("jakarta.persistence.Entity")
            .should().beAnnotatedWith(AggregateRoot.class).orShould().beAnnotatedWith(AggregateInternal.class)
            .as("[DDD_DOMAIN_ENTITY_MARKED] 도메인 @Entity 는 @AggregateRoot/@AggregateInternal 로 표시")
            .allowEmptyShould(false);

    /** 애그리거트 루트는 public static 팩토리(자기 타입 반환)를 가진다. */
    public static final ArchRule AGGREGATE_ROOT_HAS_FACTORY = classes().that()
            .areAnnotatedWith(AggregateRoot.class)
            .should(haveAPublicStaticFactoryReturningSelf())
            .as("[DDD_AGGREGATE_ROOT_HAS_FACTORY] 애그리거트 루트는 public static 팩토리를 가진다")
            .allowEmptyShould(false);

    /**
     * CORE 서브도메인은 GENERIC 서브도메인에 의존하지 않는다(전략적 의존 방향).
     * ks 에는 현재 GENERIC 타입이 없어 vacuous 통과 허용(allowEmptyShould(true)).
     */
    public static final ArchRule CORE_NOT_DEPEND_ON_GENERIC = classes().that()
            .areAnnotatedWith(Subdomain.class).and(hasSubdomain(SubdomainType.CORE))
            .should(notDependOnGenericSubdomain())
            .as("[DDD_CORE_NOT_DEPEND_ON_GENERIC] CORE 서브도메인은 GENERIC 서브도메인에 의존하지 않는다")
            .allowEmptyShould(true);

    /**
     * 요청 입력은 커맨드: @RestController 의 @RequestBody 파라미터 타입은 ..command.. 패키지이거나 *Command 명명.
     * (템플릿의 application *Request 명명 금지보다 강한 로컬 변형 — 실제 컨트롤러 입력 타입을 검사.)
     */
    public static final ArchRule REQUEST_INPUT_IS_COMMAND = classes().that()
            .areAnnotatedWith(RestController.class)
            .should(haveRequestBodyParamsAsCommand())
            // @RestController 가 실재하므로 vacuous 통과를 허용하지 않는다(어노테이션 누락 시 침묵 방지).
            .as("[DDD_REQUEST_INPUT_IS_COMMAND] @RequestBody 입력은 ..command.. 의 *Command 타입")
            .allowEmptyShould(false);

    /** 도메인에 스프링 스테레오타입/@Transactional 금지(POJO 유지). 문자열 매칭(스프링 비의존). */
    public static final ArchRule NO_SPRING_STEREOTYPES_IN_DOMAIN = noClasses().that()
            .resideInAPackage("..domain..")
            .should().beAnnotatedWith("org.springframework.stereotype.Service")
            .orShould().beAnnotatedWith("org.springframework.stereotype.Component")
            .orShould().beAnnotatedWith("org.springframework.stereotype.Repository")
            .orShould().beAnnotatedWith("org.springframework.stereotype.Controller")
            .orShould().beAnnotatedWith("org.springframework.web.bind.annotation.RestController")
            .orShould().beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
            .as("[DDD_NO_SPRING_IN_DOMAIN] 도메인에 스프링 스테레오타입/@Transactional 금지")
            .allowEmptyShould(false);

    /** 도메인 public setter 금지(빈약 모델 방지). Lombok 의 setter 생성 애너테이션이 만든 setter 도 바이트코드로 포착. */
    public static final ArchRule DOMAIN_NO_PUBLIC_SETTER = noMethods().that()
            .areDeclaredInClassesThat().resideInAPackage("..domain..")
            .should().bePublic().andShould().haveNameMatching("set[A-Z].*")
            .as("[DDD_NO_PUBLIC_SETTER] 도메인은 public setter 금지(행위 메서드로 캡슐화)")
            .allowEmptyShould(false);

    /** 도메인 내부 비결정적 API(시간/난수) 직접 호출 금지 — 호출자(application)가 주입. */
    public static final ArchRule DOMAIN_NO_NONDETERMINISTIC_API = classes().that()
            .resideInAPackage("..domain..")
            .should(notCallNonDeterministicApi())
            .as("[DDD_NO_NONDETERMINISTIC_API] 도메인에서 시간/난수 API 직접 호출 금지")
            .allowEmptyShould(false);

    /** 도메인 서비스 무상태: @DomainService 의 필드는 final. */
    public static final ArchRule DOMAIN_SERVICE_STATELESS = fields().that()
            .areDeclaredInClassesThat().areAnnotatedWith(DomainService.class)
            .should().beFinal()
            .as("[DDD_DOMAIN_SERVICE_STATELESS] 도메인 서비스는 무상태(필드 final)")
            .allowEmptyShould(true);

    /**
     * 다른 애그리거트/비즈니스 식별자 참조 필드(`customerId` 등 `*Id`)는 전용 타입(Typed ID) 사용 — raw String/Long/UUID 금지.
     * 자체 surrogate 키(`id` 또는 JPA `@Id`/`@EmbeddedId`)는 실용 레이어드(JPA) 허용을 위해 면제.
     */
    public static final ArchRule AGGREGATE_ID_FIELD_IS_TYPED = fields().that()
            .areDeclaredInClassesThat(annotatedAsAggregate())
            .should(haveTypedIdType())
            .as("[DDD_TYPED_ID] 애그리거트 간 참조 식별자는 전용 타입(Typed ID) 사용(자체 @Id surrogate는 면제)")
            .allowEmptyShould(false);

    /** 도메인 멤버(필드/생성자/메서드) 전반에 @Autowired 금지 — 필드주입 외 생성자/메서드 주입까지 차단. */
    public static final ArchRule NO_AUTOWIRED_IN_DOMAIN = noMembers().that()
            .areDeclaredInClassesThat().resideInAPackage("..domain..")
            .should().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
            .as("[DDD_NO_AUTOWIRED_IN_DOMAIN] 도메인은 @Autowired 금지(생성자 주입은 프레임워크 비의존으로)")
            .allowEmptyShould(false);

    /** 애그리거트 루트의 public 메서드가 내부 컬렉션을 날것으로 노출(List/Set/Map 반환) 금지. */
    public static final ArchRule AGGREGATE_NO_EXPOSED_MUTABLE_COLLECTION = methods().that()
            .areDeclaredInClassesThat().areAnnotatedWith(AggregateRoot.class).and().arePublic().and().areNotStatic()
            .should(notReturnRawCollection())
            .as("[DDD_NO_EXPOSED_COLLECTION] AR 은 내부 컬렉션을 날것으로 노출하지 않는다(불변 뷰/복사/스트림)")
            .allowEmptyShould(false);

    /** application 의 *Command 는 불변(record 또는 setter 없음 + 모든 인스턴스 필드 final). */
    public static final ArchRule COMMAND_IS_IMMUTABLE = classes().that()
            .resideInAPackage("..application..").and().haveSimpleNameEndingWith("Command")
            .should(beImmutableCommand())
            .as("[DDD_COMMAND_IMMUTABLE] Command 는 불변(record 또는 setter 없음·필드 final)")
            .allowEmptyShould(false);

    // ── 본 레포 자체 강화 규칙(템플릿에 없음, 더 엄격하므로 유지) ──────────────────────────

    /** 레이어 경계: application 은 infrastructure 에 의존하지 않는다(포트 사용 — DIP). 훅(휴리스틱)만 막던 경계를 CI 권위 게이트로도 강제한다. */
    public static final ArchRule APPLICATION_NOT_DEPEND_ON_INFRASTRUCTURE = noClasses().that()
            .resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..", "..infra..")
            .as("[DDD_APP_NOT_DEPEND_ON_INFRA] application 은 infrastructure 에 의존하지 않는다(포트 사용)")
            .allowEmptyShould(false);

    /** VO 회귀 방지: 도메인 @Entity 의 (비static) String 필드 금지 → @Embedded 값 객체 사용(원시 집착 차단). */
    public static final ArchRule DOMAIN_ENTITY_NO_RAW_STRING = fields().that()
            .areDeclaredInClassesThat().resideInAPackage("..domain..")
            .and().areDeclaredInClassesThat().areAnnotatedWith("jakarta.persistence.Entity")
            .and().doNotHaveModifier(JavaModifier.STATIC)
            .should().notHaveRawType(String.class)
            .as("[DDD_NO_PRIMITIVE_OBSESSION] 도메인 @Entity 의 String 필드 금지(값 객체로 포장)")
            .allowEmptyShould(true);

    // ── ArchCondition / DescribedPredicate 헬퍼 ────────────────────────────────────────

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

    private static ArchCondition<JavaClass> haveAPublicStaticFactoryReturningSelf() {
        return new ArchCondition<>("have a public static factory method returning its own type") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                boolean has = clazz.getMethods().stream().anyMatch(m ->
                        m.getModifiers().contains(JavaModifier.PUBLIC)
                                && m.getModifiers().contains(JavaModifier.STATIC)
                                && m.getRawReturnType().equals(clazz));
                if (!has) {
                    events.add(SimpleConditionEvent.violated(clazz,
                            clazz.getName() + " has no public static factory returning " + clazz.getSimpleName()));
                }
            }
        };
    }

    private static DescribedPredicate<JavaClass> hasSubdomain(SubdomainType t) {
        return new DescribedPredicate<>("@Subdomain(" + t + ")") {
            @Override
            public boolean test(JavaClass c) {
                return c.tryGetAnnotationOfType(Subdomain.class).map(s -> s.value() == t).orElse(false);
            }
        };
    }

    private static boolean isGeneric(JavaClass c) {
        return c.tryGetAnnotationOfType(Subdomain.class).map(s -> s.value() == SubdomainType.GENERIC).orElse(false);
    }

    private static ArchCondition<JavaClass> notDependOnGenericSubdomain() {
        return new ArchCondition<>("not depend on @Subdomain(GENERIC) classes") {
            @Override
            public void check(JavaClass c, ConditionEvents ev) {
                for (Dependency d : c.getDirectDependenciesFromSelf()) {
                    JavaClass t = d.getTargetClass().getBaseComponentType();
                    if (!t.equals(c) && isGeneric(t)) {
                        ev.add(SimpleConditionEvent.violated(d,
                                c.getSimpleName() + " (CORE) depends on GENERIC " + t.getSimpleName()));
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

    private static final java.util.Set<String> NONDETERMINISTIC_TIME_TYPES = java.util.Set.of(
            "java.time.LocalDateTime", "java.time.Instant", "java.time.LocalDate",
            "java.time.LocalTime", "java.time.ZonedDateTime", "java.time.OffsetDateTime");

    private static ArchCondition<JavaClass> notCallNonDeterministicApi() {
        return new ArchCondition<>("not call non-deterministic time/random APIs") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                for (JavaMethodCall call : clazz.getMethodCallsFromSelf()) {
                    String owner = call.getTargetOwner().getFullName();
                    String name = call.getName();
                    boolean bad = (NONDETERMINISTIC_TIME_TYPES.contains(owner) && name.equals("now"))
                            || (owner.equals("java.util.UUID") && name.equals("randomUUID"))
                            || (owner.equals("java.lang.Math") && name.equals("random"))
                            || (owner.equals("java.util.concurrent.ThreadLocalRandom") && name.equals("current"))
                            || (owner.equals("java.lang.System")
                                    && (name.equals("currentTimeMillis") || name.equals("nanoTime")));
                    if (bad) {
                        events.add(SimpleConditionEvent.violated(call,
                                call.getDescription() + " (inject time/id from the caller instead)"));
                    }
                }
                for (JavaConstructorCall call : clazz.getConstructorCallsFromSelf()) {
                    String owner = call.getTargetOwner().getFullName();
                    if (owner.equals("java.util.Random") || owner.equals("java.security.SecureRandom")) {
                        events.add(SimpleConditionEvent.violated(call,
                                call.getDescription() + " (inject randomness from the caller instead)"));
                    }
                }
            }
        };
    }

    private static DescribedPredicate<JavaClass> annotatedAsAggregate() {
        return new DescribedPredicate<>("annotated with @AggregateRoot or @AggregateInternal") {
            @Override
            public boolean test(JavaClass c) {
                return c.isAnnotatedWith(AggregateRoot.class) || c.isAnnotatedWith(AggregateInternal.class);
            }
        };
    }

    private static final java.util.Set<String> RAW_ID_TYPES = java.util.Set.of(
            "java.lang.String", "long", "java.lang.Long", "int", "java.lang.Integer", "java.util.UUID");

    private static ArchCondition<JavaField> haveTypedIdType() {
        return new ArchCondition<>("use a dedicated typed-ID type for cross-aggregate identifier fields") {
            @Override
            public void check(JavaField field, ConditionEvents events) {
                String n = field.getName();
                // 자체 surrogate 키(이름 'id' 또는 JPA @Id/@EmbeddedId)는 면제 — 실용 레이어드 JPA 허용.
                boolean ownSurrogate = n.equals("id")
                        || field.isAnnotatedWith("jakarta.persistence.Id")
                        || field.isAnnotatedWith("jakarta.persistence.EmbeddedId");
                // 다른 애그리거트/비즈니스 식별자 참조(customerId 등)는 전용 타입 권장.
                boolean foreignIdentifier = n.endsWith("Id") && !n.equals("id");
                if (!ownSurrogate && foreignIdentifier
                        && RAW_ID_TYPES.contains(field.getRawType().getFullName())) {
                    events.add(SimpleConditionEvent.violated(field,
                            field.getFullName() + " uses raw type " + field.getRawType().getSimpleName()
                                    + " for an aggregate reference (use a typed ID value object)"));
                }
            }
        };
    }

    private static final java.util.Set<String> RAW_COLLECTION_TYPES = java.util.Set.of(
            "java.util.List", "java.util.Set", "java.util.Map", "java.util.Collection",
            "java.util.SortedSet", "java.util.SortedMap");

    private static ArchCondition<JavaMethod> notReturnRawCollection() {
        return new ArchCondition<>("not return a raw mutable collection type") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                if (RAW_COLLECTION_TYPES.contains(method.getRawReturnType().getFullName())) {
                    events.add(SimpleConditionEvent.violated(method,
                            method.getFullName() + " returns raw " + method.getRawReturnType().getSimpleName()
                                    + " (return an unmodifiable view, defensive copy, or stream)"));
                }
            }
        };
    }

    private static ArchCondition<JavaClass> beImmutableCommand() {
        return new ArchCondition<>("be immutable (record, or no public setter and all instance fields final)") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                if (clazz.isRecord()) return;
                for (JavaMethod m : clazz.getMethods()) {
                    if (m.getModifiers().contains(JavaModifier.PUBLIC) && m.getName().matches("set[A-Z].*")) {
                        events.add(SimpleConditionEvent.violated(clazz,
                                clazz.getName() + " has public setter " + m.getName() + " (Command must be immutable)"));
                    }
                }
                for (JavaField f : clazz.getFields()) {
                    if (!f.getModifiers().contains(JavaModifier.STATIC)
                            && !f.getModifiers().contains(JavaModifier.FINAL)) {
                        events.add(SimpleConditionEvent.violated(clazz,
                                clazz.getName() + " has non-final field " + f.getName() + " (Command must be immutable)"));
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

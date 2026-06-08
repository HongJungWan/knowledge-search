package com.hris.knowledgesearch.archunit;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** 드롭인 게이트: 프로덕션 코드(com.hris.knowledgesearch, 테스트 제외)에 DDD 구조 규칙을 정밀 강제. */
@AnalyzeClasses(packages = "com.hris.knowledgesearch", importOptions = ImportOption.DoNotIncludeTests.class)
class DddArchitectureTest {
    @ArchTest static final ArchRule domainPurity = DddRules.DOMAIN_PURITY;
    @ArchTest static final ArchRule repositoryImpl = DddRules.REPOSITORY_IMPL_IN_INFRA;
    @ArchTest static final ArchRule aggregateAccess = DddRules.AGGREGATE_ACCESS;
    @ArchTest static final ArchRule idReference = DddRules.ID_REFERENCE_BETWEEN_AGGREGATES;
    @ArchTest static final ArchRule valueObjectImmutable = DddRules.VALUE_OBJECT_IMMUTABLE;
    @ArchTest static final ArchRule noFieldInjection = DddRules.NO_FIELD_INJECTION;
}

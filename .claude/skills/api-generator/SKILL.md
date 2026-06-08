---
name: api-generator
description: Spring REST 엔드포인트를 생성/수정할 때 로드. 컨트롤러 위치, Command/DTO, 검증, 응용 서비스 위임 규칙.
---

# Spring REST API 표준

- `@RestController` 는 **presentation/controller 레이어**에 둔다(도메인 밖). DTO ↔ 도메인 변환은 경계에서.
- 컨트롤러는 얇게: 요청 수신·검증·응용 서비스 호출·응답 매핑만. **비즈니스 로직 금지**(도메인에 위임).
- 외부 요청은 **Command 객체**(보편 언어 반영)로 수신, 입력 검증은 `@Valid` + Bean Validation.
- 응용 서비스(`@Service`)는 흐름 제어(트랜잭션/조회·저장/보안)만. 로직은 도메인 모델([[ddd-guidelines]]).
- 에러는 전역 `@RestControllerAdvice` 로 일관된 포맷 반환. 컨트롤러에서 try/catch 남발 금지.

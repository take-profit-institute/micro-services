# Candle protobuf 계약

Java 서비스 소스 코드와 독립적인 내부 gRPC 계약을 이 디렉터리에서 관리합니다.

```text
proto/candle/<service>/v1/<service>.proto
```

- proto 변경은 해당 서비스를 소유한 팀이 검토·승인합니다.
- `common/v1`에는 페이징, 금액, 감사 시각, 오류 상세처럼 실제 서비스 간 공통 메시지만 둡니다.
- 목록 RPC는 `candle.common.v1.PageRequest`를 사용하고 `next_page_token`을 반환합니다.
- 모든 쓰기 RPC request는 `candle.common.v1.CommandMetadata`를 포함합니다. BFF는 이 안의 `idempotency_key`와 gRPC metadata `x-idempotency-key`를 같은 값으로 전달해야 합니다.
- 새 필드는 추가 방식으로 진화시킵니다. 배포된 field 번호, RPC 이름, enum 값은 재사용하지 않으며 제거 시 `reserved`로 예약합니다.
- CI에서 `buf lint`와 `buf breaking --against <baseline>`을 실행합니다.
- Java gRPC stub은 이 디렉터리의 proto에서 생성하며, 생성된 클래스는 직접 작성하거나 수정하지 않습니다.

# AGENTS.md (Project Specific)

이 프로젝트는 글로벌 `AGENTS.md` 지침을 기본으로 따르며, 아래의 **타입 안전성 및 근본 원인 해결 규칙**을 추가로 우선 적용합니다.

---

## 1. 증상 치료가 아닌 근본 원인 해결

- **억제 어노테이션으로 가리지 마세요:** 린트/컴파일러 경고나 타입 불일치가 발생했을 때 경고를 끄는 방향으로 접근하지 마세요.
- **도메인 무결성 우선:** "이 데이터가 애초에 nullable해야 하는가?", "언어/타입 레벨에서 결함을 없앨 수 있는가?"를 먼저 고민하고 구조적 해결책(Null Object 패턴, 불변 Record/Value Object 등)을 제시하세요.

---

## 2. 외과적 변경의 정의 및 경고 억제 금지

- **'외과적 변경'은 '최소 diff 땜질'이 아닙니다:** 문제의 근본 원인을 정확히 타격하여 부작용 없는 견고한 구조를 만드는 것을 의미합니다.
- **경고나 타입 에러 억제 전면 금지:**
  - **Java**: `@SuppressWarnings("null")`, `Objects.requireNonNullElse` 남발 금지
  - **TypeScript**: `// @ts-ignore`, `// @ts-nocheck`, `as any`, `!`(non-null assertion) 남발 금지
  - **Python**: `# type: ignore`, `cast(Any, ...)`, 무의미한 `try-except: pass` 금지
- 컴파일러/린터를 침묵시키는 대신 언어별 타입 시스템(불변 객체, Non-null 보장, Null Object, Sentinel Object 등)을 활용해 근본 원인을 해결하세요.

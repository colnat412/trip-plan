# TripBudget Core — quy tắc làm việc

Đọc file này ở đầu mỗi phiên làm việc với Core, và đọc lại phần liên quan trước khi sửa code. Bối cảnh chung của dự án hiện nằm trong `../../trip-budget-web/AGENTS.md`; khi làm việc frontend phải đọc quy tắc ở đó.

## Giao tiếp và đánh giá

- Trả lời tiếng Việt, gọi người dùng là **Ăm chã húi**.
- Mỗi quyết định đáng kể phải giải thích: (1) vấn đề cốt lõi và luồng dữ liệu, (2) ưu/nhược, (3) tại sao chọn hoặc không chọn, (4) phương án khác, (5) đánh giá yêu cầu bằng bằng chứng; không tự động đồng ý.
- Nêu rõ phần đã sửa, đã kiểm chứng và giới hạn. Không nhận là đã audit bảo mật toàn bộ khi chỉ refactor.
- **Bỏ qua smoke test** theo yêu cầu người dùng. Chạy unit/integration test phù hợp với thay đổi.

## Kiến trúc và khả năng đọc

1. Luồng chính: **Controller → concrete Service → Repository → Database**.
2. Controller nhận DTO, lấy `@CurrentUser CurrentUserDto user`, khai báo đầu hàm `Long currentUserId = user.id()`, rồi gọi service. Không lấy danh tính người gọi từ request hoặc tự parse JWT.
3. Service kiểm tra quyền và điều phối transaction. Public API có thể không cần current user; API bảo vệ phải truyền danh tính đã xác thực xuống service.
4. SQL/JPQL thuộc repository; không dùng JdbcTemplate, EntityManager truy vấn trực tiếp hoặc map `Object[]` trong service. Ưu tiên derived queries đơn giản; query phức tạp dùng text block, alias rõ nghĩa, tham số bind. Không ghép input người dùng vào SQL, kể cả ORDER BY.
5. Tổng hợp dùng projection/record có tên trường. Phân trang repository dùng `Pageable`/`Page<T>`, sau đó map response đúng hợp đồng hiện tại.
6. Service cụ thể là mặc định. Chỉ thêm interface/implementation khi có nhu cầu thật; không tạo framework truy vấn chung cho một tính năng.
7. Ưu tiên hàm private có tên rõ cho logic chỉ dùng trong một service; không tách calculator chỉ để giảm số dòng. Chỉ tách helper khi có trách nhiệm độc lập rõ hoặc nhiều nơi sử dụng, như snapshot công khai, fingerprint, HTTP client. Không chỉ chuyển nguyên một khối khó hiểu sang file khác. Giữ tên hàm theo nghiệp vụ, tránh nhiều thao tác trên một dòng và tên biến khó đoán.
   Riêng chia tiền: giữ các hàm tính phân bổ và record nội bộ trong `ExpenseService`, không tách lại `ExpenseSplitCalculator` nếu chưa có nhu cầu mới rõ ràng.
   Riêng gợi ý thanh toán: người dùng chọn giữ `computeSuggestedSettlements()` là hàm private trong `SettlementService`, không tách lại calculator nếu chưa có nhu cầu mới rõ ràng.
   `services/` dành cho service điều phối nghiệp vụ. Helper snapshot ở `plantemplate/snapshot`, kiểm tra quyền mẫu ở `plantemplate/access`, HTTP client ở `plan/clients`. Không gom helper mới vào `services/` hoặc `utils/` chung chung.
8. Entity giữ các thay đổi trạng thái/invariant của chính nó; service điều phối nhiều entity. DTO phản hồi tách khỏi entity; giữ envelope `ApiResponse<T>` và HTTP status hiện có.

## Bảo toàn hành vi khi refactor

- Đọc git status/diff trước khi sửa; giữ các thay đổi có sẵn. Không commit/reset hoặc sửa frontend ngoài phạm vi yêu cầu.
- Không đổi quyền, chính sách public/private, làm tròn tiền, thứ tự chia phần dư, soft delete, API hay schema dưới danh nghĩa làm sạch code.
- Template công khai dùng allowlist snapshot; ghi chú phải opt-in. Danh sách không tải JSON snapshot. Giữ preview fingerprint, content version, transaction, khóa copy và idempotency receipt.
- Không làm mất ngày nghỉ khi copy, không sao chép người tham gia/chi tiêu/trạng thái thực thi vào trip mới.
- Migration mới phải rõ ràng; không chạy migration hoặc test phá dữ liệu lên DB ứng dụng. Core hiện không tự chạy Flyway chỉ vì có thư mục migration.

## Kiểm chứng và tài liệu

- Java 21; Maven hiện có thể chạy bằng `mvn` trong thư mục này. Kiểm tra wrapper trước khi dùng.
- Unit test: `mvn -o clean test` nếu dependency đã có trong cache; integration template mặc định bỏ qua khi thiếu biến môi trường.
- Integration PostgreSQL chỉ dùng DB dùng một lần tên `template_test` trên localhost. Xem guard và biến `TEMPLATE_TEST_DB_*` trong `PlanTemplateIntegrationTest` trước khi chạy: fixture xóa/tạo bảng.
- Dùng `clean test` cho lượt xác minh cuối để tránh chạy test class cũ còn trong target.
- Sau refactor đọc lại diff và cập nhật tài liệu liên quan. Hướng dẫn đọc luồng, quyết định và giới hạn của lượt rà soát: [docs/core-readability-review.vi.md](docs/core-readability-review.vi.md).

## Quy tắc bổ sung từ người dùng (2026-10-29)

- Trước khi thêm tính năng, đọc entity/repository/service/controller tương tự đang có và làm theo cấu trúc đó; không tự chọn cách lưu dữ liệu khác.
- Dữ liệu nghiệp vụ mới phải có Entity và JPA Repository. Ưu tiên derived query, sau đó JPQL/projection khi cần; không tự thêm JDBC/SQL trực tiếp thay cho entity.
- Repository tổng hợp JDBC hiện có không phải tiền lệ để bỏ entity cho tính năng mới.

- Ngoại lệ upload được người dùng chọn rõ: mở rộng UploadRepository JDBC đang có cho kiểm tra/claim upload; không thêm UploadAssetEntity/repository JPA song song chỉ phục vụ preview banner. Quy tắc entity cho dữ liệu nghiệp vụ mới không yêu cầu viết lại module upload hiện hữu.

## Sáu danh mục dùng chung (2026-10-30)

- Ăn uống, Di chuyển, Mua sắm, Chỗ ở, Vé, Khác; giữ mã FOOD_BEVERAGE, TRANSPORTATION, SHOPPING, ACCOMMODATION, SIGHTSEEING, OTHER. Người dùng chọn gộp ENTERTAINMENT vào OTHER.
- Chạy migration V3 trước khi triển khai enum mới. JSON alias giữ khả năng đọc payload/snapshot mẫu cũ; không sửa JSON/fingerprint công khai. Đã kiểm tra migration trên PostgreSQL dùng một lần, chưa chạy trên database ứng dụng.
- Chi tiết luồng, thứ tự triển khai và kiểm chứng: [docs/category-update.vi.md](docs/category-update.vi.md).

## Notifications and realtime (2026-10-30)

- One Core instance only until a Redis/broker bridge is implemented. New V5__notifications_realtime.sql must be applied manually before starting Core; it has only been tested on a disposable database, not applied to the application DB.
- NotificationEntity/JPA + transactional outbox are written inside existing business transactions. Preserve OWNER direct-add and VICE pending/manager-approval semantics. Exclude actor and inactive/deleted recipients; AI generation produces one grouped signal per recipient.
- JWT/cookie handshake, masked CSRF CONNECT and exact personal subscriptions are protected separately. RealtimeSessions checks expiry/session/policy at delivery and periodically; do not bypass authorization or add broad subscriptions for future chat.
- Run scripts/test-notifications.sh for guarded disposable PostgreSQL and real WebSocket integration tests; no smoke tests. Runtime setup, architecture and remaining limits: docs/notifications-implementation.vi.md.
- Scheduler infrastructure belongs in dependency-free RealtimeSchedulingConfig, not RealtimeConfig: Boot 4.1 eagerly collects AsyncTaskExecutor beans during JPA initialization and ThreadPoolTaskScheduler also implements that interface. Keep the JPA + realtime context regression test with circular references disabled; separate repository and WebSocket tests alone do not cover startup cycles.

## Four trip statuses (2026-11-04)

- User statuses: DRAFT, IN_PROGRESS, COMPLETED, CANCELLED; DELETED remains internal for existing soft deletion. Removed PLANNING, CONFIRMED and ARCHIVED from TripStatus. Old update JSON enum values are rejected through the existing 400 handler.
- Apply V8__simplify_trip_statuses.sql before starting this Core: PLANNING/CONFIRMED -> DRAFT, ARCHIVED -> COMPLETED, preserving is_del and all rows. Changed rows increment version/update timestamp; supported rows remain unchanged. Frontend types/filter constants now use canonical values directly.
- Core does not automatically execute Flyway. Migration was tested only on a disposable PostgreSQL 12 database named template_test, not applied to the application DB. Detailed rollout/order, mapping tradeoffs and checks: docs/trip-statuses.vi.md.
- mvn -o clean test: 11 pass (4 new status tests). SQL fixture passes mapping/constraints/default/version/deletion/repeat checks. Frontend TypeScript/scoped ESLint/diff checks pass. Skip smoke tests.

## Maps owned by Spring Boot Core (2026-11-05)

- User explicitly requires the separate backend to own map integration. MapsController → MapsService → VietmapClient/OsmMapsClient → external provider. Next.js BFF is transport only; do not move provider orchestration back into Next.js.
- VIETMAP_API_KEY belongs in Core .env only. Application config reads only the local .env. Tile key remains a separate browser exception in web. No map entities/database migration: provider data is ephemeral and not persisted as new business records.
- Existing JWT, Redis session and policy filters protect /maps/**. Controller uses @CurrentUser and forwards the verified ID to service. Per-user/process budgets, Photon cache, public-host throttling, coordinates and provider fallback live in Core.
- MapsRequestFilter runs after authorization, limits streaming body size and enforces HTTPS when MAPS_REQUIRE_HTTPS is enabled. Disable duplicate servlet registration. Only explicitly trusted proxies may supply X-Forwarded-Proto.
- Java HttpClient: GET, HTTPS Vietmap URL, apikey query, redirects NEVER, timeouts and sanitized errors without raw causes/keys. Keep OSM fallback and no fabricated distances.
- Read docs/maps.vi.md for code walkthrough, configuration and limits. Maps unit/security tests use mocked HTTP/Redis/users and no application DB. Skip smoke tests.

## Maps readability conventions (2026-11-05)

- User requires map integration code to be understandable to beginners/Junior and match existing modules. DTOs are separate files in dtos/request and dtos/response, using Request/Response suffixes; do not restore the MapsData container.
- Follow Plan controller constructor injection/Lombok and explicit currentUserId → concrete service → client flow. Use descriptive variables and private methods for real processing steps; avoid compressed multi-statement lines and nested stream pipelines for matrix index mapping.
- Keep JsonNode at the distance request boundary for strict input-type validation; keep response JSON names, null omission, source/fallback/cooldown and auth/rate behavior. Never add Data/toString to settings containing service keys.
- See docs/maps.vi.md for beginner reading order and the reason original matrix indices must be preserved.

## Single .env deployment (2026-11-05)

- Local/production both use a service-local .env, with environment-specific contents managed outside Git. Existing application.yml import stays optional:file:.env[.properties], relative to working directory; normal Spring source precedence remains.
- Docker /app/.env is mounted read-only at runtime. Removed baked ENV PORT=8081 so file PORT can apply; application.yml default remains 8081. Git/docker ignores protect .env variants and allow .env.example.
- Web launcher restored separately, guarding alternative env filenames. Setup/limits: ../../trip-budget-web/docs/environment.vi.md. Never print/copy real key values into docs or images.

## Single Spring Boot backend (2026-11-06)

- User explicitly requires moving both NestJS Identity and Python AI into this Spring Boot application while preserving existing business behavior and code style. This supersedes earlier read-only Identity ownership and separate Python assumptions.
- auth/user/uploads own the existing trip_core.users/trip_core.uploads data (moved from public by V9); no duplicate user table or cross-module FK. UserEntity is writable with behavior methods. UploadRepository remains the existing JDBC exception; avatar JPA/JDBC operations share the transaction/DataSource.
- Preserve bcrypt cost 12 and Node 72-byte truncation (PasswordHasher), JWT HS256 and claims/TTLs, Redis key/JSON/rotation/revocation behavior, policy gates, OTP and Google account rules. Do not silently redesign auth during migration cleanup.
- Preserve AI prompts, model order, temperature, 503 retry, receipt defaults/types/size and existing plan persistence. AiPlanClient delegates locally to AiService, GeminiClient owns external HTTP. Never restore runtime calls to Python.
- Browser BFF calls one server-only CORE_SERVICE_URL. Private service URLs/keys do not belong in NEXT_PUBLIC variables.
- scripts/test-single-backend.sh provisions isolated temporary PostgreSQL template_test and Redis and runs clean tests; never run its destructive fixtures against an application DB. Keep integration tests opt-in/guarded. External providers are mocked; skip smoke tests.
- Read docs/single-backend-migration.vi.md for migration contract, retained legacy behaviors, code reading order, rollout and limitations. Legacy source directories remain for comparison; they are not runtime services.

- Startup regression (2026-11-06): retain spring-boot-starter-restclient for RestClient.Builder auto-configuration. SingleBackendIntegrationTest must instantiate real GeminiClient/CloudinaryClient beans (spies with doReturn on external calls), so missing constructor dependencies cannot be hidden by full mocks.

## Controller consolidation (2026-11-06)

- User prefers keeping small related operations together. Policy acceptance belongs in UserController at /users/me/policies/accept; do not restore a one-endpoint PoliciesController without an independent policy API responsibility.
- IdentityExceptionHandler was consolidated into GlobalExceptionHandler. Retain former Identity error contracts for AuthController/UserController/UploadController, identifying the matched HandlerMethod. Keep Core/Ai/Maps error formats and HTTP behavior; do not standardize contracts silently during readability refactors. LoginResult remains a separate internal record per DTO conventions.
- SingleBackendIntegrationTest covers the combined policy route and Identity/Core/AI error response boundaries. scripts/test-single-backend.sh: 32 pass on disposable services; skip smoke tests.

## Single application schema (2026-11-06)

- User explicitly authorized moving the real application's database objects to trip_core. V9__move_identity_to_trip_core.sql moves public.users/uploads and both upload enums in place; owned users_id_seq/indexes follow automatically. All active entity/native SQL/JDBC/test references now target trip_core.
- On this workspace database V9 was applied after a restorable scoped backup and rehearsal on a disposable clone. Transactional verification checked all 18 application tables and 17 sequences, data fingerprints/counts, table/storage OIDs, grants, constraints, indexes and enum labels/OIDs before commit. Other schema ai_speaking and public UUID extension were untouched.
- Do not restore public references or run legacy NestJS against the migrated DB. Other deployments must apply V9 manually before starting this code; Hibernate remains validate and Flyway is not automatically enabled.
- scripts/test-trip-core-schema.sh checks populated fixtures, identities/FKs/defaults/views/grants, repeated execution and atomic rejection/rollback for target collisions. scripts/test-single-backend.sh: 32 pass, 0 skip, with all auth/upload fixtures in trip_core. No smoke tests or destructive application DB fixtures.
- See docs/single-schema.vi.md for rollout, verification, protected backup location and rollback procedure. General rule against unrequested live migrations remains; this explicit schema migration authorization does not authorize unrelated data/schema changes.

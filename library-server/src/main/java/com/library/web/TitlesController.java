package com.library.web;

import com.library.contract.ModuleResult;
import com.library.contract.error.StableErrorCode;
import com.library.contract.idempotency.IdempotencyScope;
import com.library.contract.model.PageCursor;
import com.library.contract.security.ActorContext;
import com.library.catalog.contract.MaintainTitle;
import com.library.catalog.contract.TitleView;
import com.library.application.maintenance.MaintenanceUseCases;
import com.library.web.Envelope;
import com.library.web.support.ApiResponses;
import com.library.web.support.Actors;
import com.library.web.support.SuccessCodes;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * OpenAPI 操作 searchTitles/createTitle/getTitle/updateTitle/removeTitle/getReadingContent（Catalog）。
 */
@RestController
@RequestMapping("/api/v1")
public class TitlesController {

    private static final String FIELD_ERROR = StableErrorCode.MLS_CAT_001.code();

    private final MaintenanceUseCases maintenance;
    private final com.library.contract.idempotency.IdempotencyService idempotency;
    private final ApiResponses responses;

    public TitlesController(MaintenanceUseCases maintenance,
                            com.library.contract.idempotency.IdempotencyService idempotency,
                            ApiResponses responses) {
        this.maintenance = maintenance;
        this.idempotency = idempotency;
        this.responses = responses;
    }

    public record CreateTitleWebRequest(String isbn, String title, List<String> authors, String category,
                                        String publisher, Integer publishYear, Long priceCents) {
    }

    public record UpdateTitleWebRequest(String title, List<String> authors, String category,
                                        String publisher, Integer publishYear, Long priceCents,
                                        Long version) {
    }

    public record VersionReasonWebRequest(Long version, String reason) {
    }

    // ---- 公开门户 -----------------------------------------------------------

    @GetMapping("/titles")
    public ResponseEntity<Envelope> searchTitles(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String availability,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        var result = maintenance.search(q, category, availability, PageCursor.of(cursor, limit));
        return responses.forResult(result, SuccessCodes.of("searchTitles"), page -> {
            var data = new java.util.LinkedHashMap<String, Object>();
            data.put("items", page.items().stream().map(s -> Map.of(
                    "titleId", String.valueOf(s.titleId()),
                    "title", s.title(),
                    "authors", s.authors(),
                    "category", s.category() == null ? "" : s.category(),
                    "availability", s.availability())).toList());
            data.put("nextCursor", page.nextCursor());
            return data;
        });
    }

    @GetMapping("/titles/{titleId}")
    public ResponseEntity<Envelope> getTitle(@PathVariable long titleId) {
        var result = maintenance.title(titleId);
        return responses.forResult(result, SuccessCodes.of("getTitle"), TitlesController::toTitleData);
    }

    @GetMapping("/titles/{titleId}/content")
    public ResponseEntity<Envelope> getReadingContent(
            @PathVariable long titleId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        var result = maintenance.readingContent(titleId, PageCursor.of(cursor, limit));
        return responses.forResult(result, SuccessCodes.of("getReadingContent"), content -> {
            var data = new java.util.LinkedHashMap<String, Object>();
            data.put("contentType", content.contentType());
            data.put("pages", content.pages().stream().map(p -> Map.of(
                    "pageNo", p.pageNo(), "text", p.text())).toList());
            data.put("nextCursor", content.nextCursor());
            return data;
        });
    }

    // ---- 馆员维护 -----------------------------------------------------------

    @PostMapping("/titles")
    public ResponseEntity<Envelope> createTitle(
            HttpServletRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody CreateTitleWebRequest body) {
        var actor = Actors.actor(request);
        if (actor == null) {
            return unauthorized("/api/v1/titles");
        }
        var command = new MaintainTitle("CREATE", null, body.isbn(), body.title(), body.authors(),
                body.category(), body.publisher(), body.publishYear(), body.priceCents(), null, null);
        var outcome = idempotency.execute(
                new IdempotencyScope(actor.accountId().asString(), "POST", "/api/v1/titles"),
                idempotencyKey,
                com.library.contract.idempotency.JdbcIdempotencyService.sha256(
                        body.isbn() + "" + body.title() + "" + body.authors() + "" + body.category()
                                + "" + body.publisher() + "" + body.publishYear() + "" + body.priceCents()),
                SuccessCodes.of("createTitle"),
                com.library.catalog.contract.TitleChange.class,
                () -> maintenance.createTitle(actor, command));
        return respondWithTitle(request, outcome, "/api/v1/titles");
    }

    @PatchMapping("/titles/{titleId}")
    public ResponseEntity<Envelope> updateTitle(
            HttpServletRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable long titleId,
            @RequestBody UpdateTitleWebRequest body) {
        var actor = Actors.actor(request);
        if (actor == null) {
            return unauthorized(path(titleId));
        }
        if (body.version() == null || (body.title() == null && body.authors() == null && body.category() == null
                && body.publisher() == null && body.publishYear() == null && body.priceCents() == null)) {
            return fieldError(path(titleId), "PATCH 必须携带 version 与至少一个可维护字段");
        }
        var command = new MaintainTitle("UPDATE", titleId, null, body.title(), body.authors(),
                body.category(), body.publisher(), body.publishYear(), body.priceCents(), body.version(), null);
        var outcome = idempotency.execute(
                new IdempotencyScope(actor.accountId().asString(), "PATCH", path(titleId)),
                idempotencyKey,
                com.library.contract.idempotency.JdbcIdempotencyService.sha256(
                        titleId + "" + body.title() + "" + body.authors() + "" + body.category()
                                + "" + body.publisher() + "" + body.publishYear() + "" + body.priceCents()
                                + "" + body.version()),
                SuccessCodes.of("updateTitle"),
                com.library.catalog.contract.TitleChange.class,
                () -> maintenance.updateTitle(actor, command));
        return respondWithTitle(request, outcome, path(titleId));
    }

    @DeleteMapping("/titles/{titleId}")
    public ResponseEntity<Envelope> removeTitle(
            HttpServletRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable long titleId,
            @RequestBody VersionReasonWebRequest body) {
        var actor = Actors.actor(request);
        if (actor == null) {
            return unauthorized(path(titleId));
        }
        if (body.version() == null) {
            return fieldError(path(titleId), "DELETE 必须携带 version");
        }
        var command = new MaintainTitle("REMOVE", titleId, null, null, null, null, null, null,
                null, body.version(), body.reason());
        var outcome = idempotency.execute(
                new IdempotencyScope(actor.accountId().asString(), "DELETE", path(titleId)),
                idempotencyKey,
                com.library.contract.idempotency.JdbcIdempotencyService.sha256(
                        titleId + "" + body.version() + "" + body.reason()),
                SuccessCodes.of("removeTitle"),
                com.library.catalog.contract.TitleChange.class,
                () -> maintenance.removeTitle(actor, command));
        var mapped = responses.forResult(outcome.result(), outcome.code(),
                Actors.actorId(request), path(titleId),
                change -> Map.of("status", "REMOVED"));
        return mapped;
    }

    // ---- 映射 ----------------------------------------------------------------

    private static Map<String, Object> toTitleData(TitleView view) {
        var data = new java.util.LinkedHashMap<String, Object>();
        data.put("titleId", String.valueOf(view.titleId()));
        data.put("title", view.title());
        data.put("authors", view.authors());
        data.put("category", view.category() == null ? "" : view.category());
        data.put("availability", view.availability());
        data.put("isbn", view.isbn() == null ? "" : view.isbn());
        data.put("publisher", view.publisher());
        data.put("publishYear", view.publishYear());
        data.put("priceCents", view.priceCents());
        data.put("version", view.version());
        data.put("hasReadingContent", view.hasReadingContent());
        return data;
    }

    private ResponseEntity<Envelope> respondWithTitle(HttpServletRequest request,
                                                      com.library.contract.idempotency.IdempotentOutcome<com.library.catalog.contract.TitleChange> outcome,
                                                      String auditPath) {
        var mapped = responses.forResult(outcome.result(), outcome.code(),
                Actors.actorId(request), auditPath,
                change -> {
                    var view = maintenance.title(change.titleId());
                    if (view instanceof ModuleResult.Accepted<TitleView> accepted) {
                        return toTitleData(accepted.value());
                    }
                    return Map.of("titleId", String.valueOf(change.titleId()));
                });
        return mapped;
    }

    private ResponseEntity<Envelope> unauthorized(String path) {
        return responses.forResult(
                ModuleResult.Rejected.of(StableErrorCode.MLS_ID_001.code(), "未认证：需要登录"),
                StableErrorCode.MLS_ID_001.code(), null, path, null, null);
    }

    private ResponseEntity<Envelope> fieldError(String path, String message) {
        Actors.declareFieldErrorCode(request(), FIELD_ERROR);
        return ResponseEntity.badRequest().body(
                Envelope.of(FIELD_ERROR, message, Map.of("reasons", List.of(message))));
    }

    private HttpServletRequest request() {
        var attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attributes instanceof org.springframework.web.context.request.ServletRequestAttributes servlet) {
            return servlet.getRequest();
        }
        throw new IllegalStateException("无请求上下文");
    }

    private static String path(long titleId) {
        return "/api/v1/titles/" + titleId;
    }
}

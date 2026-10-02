package com.library.web;

import com.library.application.reviewing.ReviewedActionProvider;
import com.library.contract.ModuleResult;
import com.library.contract.error.StableErrorCode;
import com.library.contract.idempotency.*;
import com.library.contract.model.PageCursor;
import com.library.review.contract.*;
import com.library.web.support.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1/reviews")
public class ReviewController {
    private final ReviewRequesting requesting;private final ReviewQuery query;private final ReviewDeciding deciding;private final ReviewedActionProvider actions;private final IdempotencyService idempotency;private final ApiResponses responses;
    public ReviewController(ReviewRequesting r,ReviewQuery q,ReviewDeciding d,ReviewedActionProvider a,IdempotencyService i,ApiResponses p){requesting=r;query=q;deciding=d;actions=a;idempotency=i;responses=p;}
    public record Request(String targetType,String targetId,String action,Map<String,Object> payload,String reason){}
    public record Decision(String decision,String reason){}
    @GetMapping public ResponseEntity<Envelope> list(HttpServletRequest req,@RequestParam(required=false)String status,@RequestParam(required=false)String cursor,@RequestParam(required=false)Integer limit){var actor=Actors.actor(req);if(actor==null)return unauthorized("/api/v1/reviews");if(actor.role()!=com.library.contract.model.Role.LIBRARIAN&&actor.role()!=com.library.contract.model.Role.ADMIN)return forbidden("/api/v1/reviews");return responses.forResult(query.reviews(new ReviewQuery.ReviewCriteria(status,PageCursor.of(cursor,limit))),SuccessCodes.of("listReviews"),v->{var m=new LinkedHashMap<String,Object>();m.put("items",v.items().stream().map(x->Map.of("reviewId",Long.toString(x.reviewId()),"targetType",x.targetType(),"targetId",Long.toString(x.targetId()),"action",x.action(),"status",x.status(),"requestedAt",x.requestedAt().toString())).toList());m.put("nextCursor",v.nextCursor());return m;});}
    @PostMapping public ResponseEntity<Envelope> request(HttpServletRequest req,@RequestHeader("Idempotency-Key")String key,@RequestBody Request b){var actor=Actors.actor(req);if(actor==null)return unauthorized("/api/v1/reviews");if(actor.role()!=com.library.contract.model.Role.LIBRARIAN)return forbidden("/api/v1/reviews");var path="/api/v1/reviews";var out=idempotency.execute(new IdempotencyScope(actor.accountId().asString(),"POST",path),key,JdbcIdempotencyService.sha256(String.valueOf(b)),SuccessCodes.of("requestReview"),Review.class,()->requesting.request(new ReviewCommand(actor.accountId().value(),b.targetType(),Long.parseLong(b.targetId()),b.action(),b.payload(),b.reason(),0)));return responses.forResult(out.result(),out.code(),v->Map.of("reviewId",Long.toString(v.reviewId()),"status",v.status()));}
    @PostMapping("/{reviewId}/decision")
    public ResponseEntity<Envelope> decide(HttpServletRequest req,
                                           @RequestHeader("Idempotency-Key") String key,
                                           @PathVariable long reviewId,
                                           @RequestBody Decision body) {
        var actor = Actors.actor(req);
        var path = "/api/v1/reviews/" + reviewId + "/decision";
        if (actor == null) return unauthorized(path);
        if (actor.role() != com.library.contract.model.Role.ADMIN) return forbidden(path);
        var detail = query.review(reviewId);
        if (detail instanceof ModuleResult.Rejected<ReviewQuery.ReviewDetail> rejected) {
            return responses.forResult(rejected, SuccessCodes.of("decideReview"), value -> Map.of());
        }
        var review = ((ModuleResult.Accepted<ReviewQuery.ReviewDetail>) detail).value();
        try {
            var outcome = idempotency.execute(
                    new IdempotencyScope(actor.accountId().asString(), "POST", path), key,
                    JdbcIdempotencyService.sha256(String.valueOf(body)), SuccessCodes.of("decideReview"),
                    ReviewDeciding.ReviewDecision.class,
                    () -> deciding.decide(new ReviewDeciding.DecideReview(reviewId, body.decision(),
                            body.reason(), actor.accountId().value()), actions.actionFor(review)));
            return responses.forResult(outcome.result(), outcome.code(), value -> {
                var data = new LinkedHashMap<String, Object>();
                data.put("reviewId", Long.toString(value.reviewId()));
                data.put("status", value.status());
                data.put("targetOutcome", value.targetOutcome());
                return data;
            });
        } catch (ReviewExecutionAborted aborted) {
            ModuleResult.Rejected<ReviewDeciding.ReviewDecision> rejected = aborted.currentVersion() == null
                    ? ModuleResult.Rejected.of(aborted.code(), aborted.getMessage())
                    : ModuleResult.Rejected.conflict(aborted.code(), aborted.currentVersion(), aborted.getMessage());
            return responses.forResult(rejected, aborted.code(), actor.accountId().value(), path, null, null);
        }
    }
    private ResponseEntity<Envelope> unauthorized(String path){return responses.forResult(ModuleResult.Rejected.of(StableErrorCode.MLS_ID_001.code(),"未认证：需要登录"),StableErrorCode.MLS_ID_001.code(),null,path,null,null);}
    private ResponseEntity<Envelope> forbidden(String path){return responses.forResult(ModuleResult.Rejected.of(StableErrorCode.MLS_ID_005.code(),"无权执行复核操作"),StableErrorCode.MLS_ID_005.code(),null,path,null,null);}
}

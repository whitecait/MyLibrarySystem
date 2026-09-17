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
    @GetMapping public ResponseEntity<Envelope> list(HttpServletRequest req,@RequestParam(required=false)String status,@RequestParam(required=false)String cursor,@RequestParam(required=false)Integer limit){if(Actors.actor(req)==null)return unauthorized("/api/v1/reviews");return responses.forResult(query.reviews(new ReviewQuery.ReviewCriteria(status,PageCursor.of(cursor,limit))),SuccessCodes.of("listReviews"),v->{var m=new LinkedHashMap<String,Object>();m.put("items",v.items().stream().map(x->Map.of("reviewId",Long.toString(x.reviewId()),"targetType",x.targetType(),"targetId",Long.toString(x.targetId()),"action",x.action(),"status",x.status(),"requestedAt",x.requestedAt().toString())).toList());m.put("nextCursor",v.nextCursor());return m;});}
    @PostMapping public ResponseEntity<Envelope> request(HttpServletRequest req,@RequestHeader("Idempotency-Key")String key,@RequestBody Request b){var actor=Actors.actor(req);if(actor==null)return unauthorized("/api/v1/reviews");var path="/api/v1/reviews";var out=idempotency.execute(new IdempotencyScope(actor.accountId().asString(),"POST",path),key,JdbcIdempotencyService.sha256(String.valueOf(b)),SuccessCodes.of("requestReview"),Review.class,()->requesting.request(new ReviewCommand(actor.accountId().value(),b.targetType(),Long.parseLong(b.targetId()),b.action(),b.payload(),b.reason(),0)));return responses.forResult(out.result(),out.code(),v->Map.of("reviewId",Long.toString(v.reviewId()),"status",v.status()));}
    @PostMapping("/{reviewId}/decision") public ResponseEntity<Envelope> decide(HttpServletRequest req,@RequestHeader("Idempotency-Key")String key,@PathVariable long reviewId,@RequestBody Decision b){var actor=Actors.actor(req);if(actor==null)return unauthorized("/api/v1/reviews/"+reviewId+"/decision");var detail=query.review(reviewId);if(detail instanceof ModuleResult.Rejected<ReviewQuery.ReviewDetail> rejected)return responses.forResult(rejected,SuccessCodes.of("decideReview"),v->Map.of());var d=((ModuleResult.Accepted<ReviewQuery.ReviewDetail>)detail).value();var path="/api/v1/reviews/"+reviewId+"/decision";var out=idempotency.execute(new IdempotencyScope(actor.accountId().asString(),"POST",path),key,JdbcIdempotencyService.sha256(String.valueOf(b)),SuccessCodes.of("decideReview"),ReviewDeciding.ReviewDecision.class,()->deciding.decide(new ReviewDeciding.DecideReview(reviewId,b.decision(),b.reason(),actor.accountId().value()),actions.actionFor(d)));return responses.forResult(out.result(),out.code(),v->{var m=new LinkedHashMap<String,Object>();m.put("reviewId",Long.toString(v.reviewId()));m.put("status",v.status());m.put("targetOutcome",v.targetOutcome());return m;});}
    private ResponseEntity<Envelope> unauthorized(String path){return responses.forResult(ModuleResult.Rejected.of(StableErrorCode.MLS_ID_001.code(),"未认证：需要登录"),StableErrorCode.MLS_ID_001.code(),null,path,null,null);}
}

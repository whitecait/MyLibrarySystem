package com.library.review.internal.adapter.persistence;

import java.time.Instant;
import java.util.*;

public final class InMemoryReviewStore implements ReviewStore {
    private final Map<Long,StoredReview> rows=new LinkedHashMap<>(); private long sequence;
    public long insert(NewReview c){var id=++sequence;rows.put(id,new StoredReview(id,c.applicantId(),c.targetType(),c.targetId(),c.action(),c.payload(),c.reason(),"PENDING",c.targetVersion(),null,0,Instant.parse("2026-09-17T00:00:00Z")));return id;}
    public Optional<StoredReview> byId(long id){return Optional.ofNullable(rows.get(id));}
    public List<StoredReview> list(String decision,long afterId,int limit){return rows.values().stream().filter(r->r.id()>afterId).filter(r->decision==null||decision.equals(r.decision())).limit(limit).toList();}
    public boolean decide(long id,long version,String decision,long decidedBy,String outcome){var r=rows.get(id);if(r==null||r.version()!=version||!"PENDING".equals(r.decision()))return false;rows.put(id,new StoredReview(r.id(),r.applicantId(),r.targetType(),r.targetId(),r.action(),r.payload(),r.reason(),decision,r.targetVersion(),outcome,r.version()+1,r.createdAt()));return true;}
}

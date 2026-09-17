package com.library.circulation.internal.adapter.persistence;

import java.time.LocalDate;
import java.util.*;

public final class InMemoryClosingStore implements ClosingStore {
    private boolean draining;private long epoch;private final Map<LocalDate,String>batches=new HashMap<>();
    public void beginDraining(LocalDate d){draining=true;}
    public String isolateAndRun(LocalDate d){batches.put(d,"SUCCEEDED");return "SUCCEEDED";}
    public Failure markFailure(LocalDate d){batches.put(d,"MANUAL");return new Failure(2,1,"MANUAL");}
    public boolean caughtUpThrough(LocalDate d){return batches.entrySet().stream().filter(e->!e.getKey().isAfter(d)).allMatch(e->"SUCCEEDED".equals(e.getValue()));}
    public long reopen(LocalDate d){draining=false;return ++epoch;}
    public boolean draining(){return draining;}
}

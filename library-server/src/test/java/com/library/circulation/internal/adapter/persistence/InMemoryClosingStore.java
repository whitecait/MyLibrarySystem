package com.library.circulation.internal.adapter.persistence;

import java.time.LocalDate;
import java.util.*;

public final class InMemoryClosingStore implements ClosingStore {
    private boolean draining;private long epoch;private LocalDate firstBusinessDate;private final Map<LocalDate,String>batches=new HashMap<>();
    public Optional<LocalDate> firstUnclosedDateThrough(LocalDate inclusiveDate){
        if(firstBusinessDate==null)return Optional.of(inclusiveDate);
        for(var date=firstBusinessDate;!date.isAfter(inclusiveDate);date=date.plusDays(1))
            if(!"SUCCEEDED".equals(batches.get(date)))return Optional.of(date);
        return Optional.empty();
    }
    public boolean requiresManualRecovery(LocalDate d){return "MANUAL".equals(batches.get(d));}
    public void beginDraining(LocalDate d){draining=true;}
    public String isolateAndRun(LocalDate d){batches.put(d,"SUCCEEDED");if(firstBusinessDate==null)firstBusinessDate=d;return "SUCCEEDED";}
    public Failure markFailure(LocalDate d){batches.put(d,"MANUAL");return new Failure(2,1,"MANUAL");}
    public boolean caughtUpThrough(LocalDate d){return firstUnclosedDateThrough(d).isEmpty();}
    public OptionalLong reopen(LocalDate d){if(!caughtUpThrough(d))return OptionalLong.empty();draining=false;return OptionalLong.of(++epoch);}
    public boolean draining(){return draining;}
}

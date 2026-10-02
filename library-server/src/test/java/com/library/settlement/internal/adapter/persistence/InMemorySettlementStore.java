package com.library.settlement.internal.adapter.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

public final class InMemorySettlementStore implements SettlementStore {
    private final Map<Long, FineRow> fines = new LinkedHashMap<>();
    private final Map<Long, OrderRow> orders = new LinkedHashMap<>();
    private final Map<String, CallbackRow> callbacks = new HashMap<>();
    private final Map<LocalDate, ReconciliationRow> reconciliations = new HashMap<>();
    private long fineSeq, orderSeq, refundSeq, reconciliationSeq;

    public Optional<FineRow> fineBySourceTx(long id) { return fines.values().stream().filter(f -> f.sourceTxId() == id).findFirst(); }
    public long insertFine(NewFine c) { var id=++fineSeq; fines.put(id,new FineRow(id,c.readerId(),c.sourceTxId(),c.amountCents(),"DUE",Instant.parse("2026-09-17T00:00:00Z"))); return id; }
    public Optional<DebtSummary> debtFor(long readerId) { var due=fines.values().stream().filter(f->f.readerId()==readerId&&"DUE".equals(f.status())).toList(); return Optional.of(new DebtSummary(due.size(),due.stream().mapToLong(FineRow::amountCents).sum())); }
    public Optional<FineRow> fineById(long id) { return Optional.ofNullable(fines.get(id)); }
    public List<FineRow> listFines(Long readerId,String status,long afterId,int limit) { return fines.values().stream().filter(f->f.id()>afterId).filter(f->readerId==null||f.readerId()==readerId).filter(f->status==null||status.equals(f.status())||("UNPAID".equals(status)&&"DUE".equals(f.status()))).limit(limit).toList(); }
    public List<OrderRow> listOccupiedOrders(long readerId,long afterId,int limit) {
        return orders.values().stream()
                .filter(o -> o.id() > afterId && "OCCUPIED".equals(o.status()))
                .filter(o -> {
                    var fine = fines.get(o.fineId());
                    return fine != null && fine.readerId() == readerId;
                })
                .sorted(Comparator.comparingLong(OrderRow::id))
                .limit(limit)
                .toList();
    }
    public Optional<OrderRow> orderById(long id) { return Optional.ofNullable(orders.get(id)); }
    public Optional<OrderRow> lockOrderById(long id) { return orderById(id); }
    public void lockCallbackEvent(String eventId) {}
    public Optional<OrderRow> unreleasedOrder(long fineId) { return orders.values().stream().filter(o->o.fineId()==fineId&&"OCCUPIED".equals(o.status())).findFirst(); }
    public long insertOrder(long fineId,long amount,Instant expires) { var id=++orderSeq; orders.put(id,new OrderRow(id,fineId,amount,"OCCUPIED",expires,0)); return id; }
    public boolean transitionOrder(long id,String expected,String next,Instant at) { var o=orders.get(id); if(o==null||!expected.equals(o.status()))return false; orders.put(id,new OrderRow(o.id(),o.fineId(),o.amountCents(),next,o.expiresAt(),o.version()+1)); return true; }
    public Optional<CallbackRow> callback(String eventId) { return Optional.ofNullable(callbacks.get(eventId)); }
    public void insertCallback(CallbackRow c) { callbacks.put(c.eventId(),c); }
    public void settleFine(long id,Instant at) { var f=fines.get(id); fines.put(id,new FineRow(f.id(),f.readerId(),f.sourceTxId(),f.amountCents(),"SETTLED",f.createdAt())); }
    public long insertRefund(long orderId,long amount,long reviewId) { return ++refundSeq; }
    public long refundCount() { return refundSeq; }
    public Optional<ReconciliationRow> reconciliation(LocalDate d) { return Optional.ofNullable(reconciliations.get(d)); }
    public long insertReconciliation(LocalDate d,Map<String,Integer> counts,List<String> exceptions) { var id=++reconciliationSeq; reconciliations.put(d,new ReconciliationRow(id,d,counts,exceptions)); return id; }
    public long reconciliationCount() { return reconciliationSeq; }
}

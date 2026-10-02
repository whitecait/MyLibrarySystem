package com.library.delivery.internal.adapter.persistence;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.*;
import java.util.List;

@Repository
public class PostgresDeliveryStore implements DeliveryStore {
    private final JdbcTemplate jdbc;
    private static final RowMapper<NotificationRow> NOTE=(rs,i)->new NotificationRow(rs.getLong("id"),rs.getLong("reader_id"),rs.getString("kind"),rs.getLong("biz_ref"),rs.getDate("reservation_date").toLocalDate(),rs.getObject("read_at")==null?null:rs.getObject("read_at",OffsetDateTime.class).toInstant(),rs.getObject("created_at",OffsetDateTime.class).toInstant());
    private static final RowMapper<AuditRow> AUDIT=(rs,i)->new AuditRow(rs.getLong("id"),rs.getString("event_type"),rs.getObject("actor_id")==null?null:rs.getLong("actor_id"),rs.getObject("related_id")==null?null:rs.getLong("related_id"),rs.getString("outcome"),rs.getObject("occurred_at",OffsetDateTime.class).toInstant());
    public PostgresDeliveryStore(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Override
    public List<NotificationRow> notifications(long reader, boolean unread, long after, int limit) {
        // The existing numeric cursor identifies an owned row; its full ordering key
        // is resolved in the same query. No new public cursor format is introduced.
        var sql = """
                SELECT n.* FROM notification n
                WHERE n.reader_id = ?
                  AND (? = 0 OR (n.reservation_date, n.created_at, n.id) >
                      (SELECT c.reservation_date, c.created_at, c.id
                       FROM notification c WHERE c.reader_id = ? AND c.id = ?))
                """ + (unread ? " AND n.read_at IS NULL" : "")
                + " ORDER BY n.reservation_date, n.created_at, n.id LIMIT ?";
        return jdbc.query(sql, NOTE, reader, after, reader, after, limit);
    }
    @Override public boolean markRead(long id,long reader,Instant at){return jdbc.update("UPDATE notification SET read_at=COALESCE(read_at,?) WHERE id=? AND reader_id=?",java.sql.Timestamp.from(at),id,reader)==1;}
    @Override public List<AuditRow> audits(Long related,Long actor,String type,long after,int limit){var sql=new StringBuilder("SELECT id,event_type,actor_id,NULLIF(detail->>'relatedId','')::bigint AS related_id,detail->>'outcome' AS outcome,occurred_at FROM audit_event WHERE id>?");var args=new java.util.ArrayList<Object>();args.add(after);if(related!=null){sql.append(" AND detail->>'relatedId'=?");args.add(Long.toString(related));}if(actor!=null){sql.append(" AND actor_id=?");args.add(actor);}if(type!=null){sql.append(" AND event_type=?");args.add(type);}sql.append(" ORDER BY id LIMIT ?");args.add(limit);return jdbc.query(sql.toString(),AUDIT,args.toArray());}
    @Override public boolean claimReceipt(String consumer,long event){try{return jdbc.update("INSERT INTO event_consumer_receipt(consumer_id,event_id) VALUES (?,?)",consumer,event)==1;}catch(DataIntegrityViolationException e){return false;}}
    @Override public void insertNotification(long reader,String kind,long ref,LocalDate date){jdbc.update("DELETE FROM notification WHERE reader_id=? AND biz_ref=? AND kind<>?",reader,ref,kind);jdbc.update("INSERT INTO notification(reader_id,kind,biz_ref,reservation_date) SELECT ?,?,?,? WHERE NOT EXISTS(SELECT 1 FROM notification WHERE reader_id=? AND biz_ref=? AND kind=?)",reader,kind,ref,java.sql.Date.valueOf(date),reader,ref,kind);}
}

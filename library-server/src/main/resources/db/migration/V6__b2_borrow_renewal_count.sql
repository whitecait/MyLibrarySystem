-- V6: B2 流通续借计数（实现型迁移，不改写 V1~V5）
-- 借阅事务无续借次数字段（V1），续借上限守卫（MLS-CIRC-012 reasons LIMIT_REACHED）
-- 需要持久化计数；每次续借原子 +1，CAS 由行 version 承担。
ALTER TABLE borrow_transaction
    ADD COLUMN renewal_count INT NOT NULL DEFAULT 0;

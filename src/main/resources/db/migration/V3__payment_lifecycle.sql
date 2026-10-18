-- Append-only migration: do not change already applied V1/B1/V2 checksums.
CREATE TABLE payment_refund (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    refund_no VARCHAR(64) NOT NULL,
    payment_id BIGINT NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL,
    release_sold_seats BOOLEAN NOT NULL,
    provider_refund_no VARCHAR(128),
    failure_message VARCHAR(512),
    created_at DATETIME(3) NOT NULL,
    refunded_at DATETIME(3),
    CONSTRAINT uk_refund_no UNIQUE (refund_no),
    CONSTRAINT uk_refund_payment UNIQUE (payment_id),
    CONSTRAINT fk_refund_payment FOREIGN KEY (payment_id) REFERENCES payment_transaction(id),
    CONSTRAINT chk_refund_positive_amount CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Existing merchant records may predate the simulator's provider ledger.
-- Do not fabricate historical provider balances: unresolved rows are reviewed through tasks.

CREATE TABLE payment_task (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    payment_id BIGINT NOT NULL,
    type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(3) NOT NULL,
    lease_until DATETIME(3),
    lease_token VARCHAR(64),
    last_error VARCHAR(512),
    CONSTRAINT uk_payment_task_type UNIQUE (payment_id, type),
    CONSTRAINT fk_payment_task_payment FOREIGN KEY (payment_id) REFERENCES payment_transaction(id),
    INDEX idx_payment_task_due (status, next_attempt_at, id),
    INDEX idx_payment_task_lease (status, lease_until, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Simulated provider data has no foreign key to the merchant's business tables.
-- Its transactions commit independently, just like an external payment platform.
CREATE TABLE mock_payment_order (
    payment_no VARCHAR(64) NOT NULL PRIMARY KEY,
    provider_trade_no VARCHAR(128) NOT NULL UNIQUE,
    amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(16) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    pay_url VARCHAR(2048) NOT NULL,
    paid_at DATETIME(3),
    failure_code VARCHAR(64),
    failure_message VARCHAR(512)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE mock_payment_refund (
    refund_no VARCHAR(64) NOT NULL PRIMARY KEY,
    payment_no VARCHAR(64) NOT NULL,
    provider_refund_no VARCHAR(128) NOT NULL UNIQUE,
    amount DECIMAL(10,2) NOT NULL,
    refunded_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_mock_refund_payment UNIQUE (payment_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO payment_task(payment_id, type, status, attempts, next_attempt_at)
SELECT id, 'SYNC', 'PENDING', 0, CURRENT_TIMESTAMP(3)
FROM payment_transaction WHERE status IN ('CREATED','PAYING','CLOSED','FAILED');

INSERT INTO payment_refund(refund_no,payment_id,request_id,amount,reason,status,release_sold_seats,created_at)
SELECT CONCAT('ref_',REPLACE(UUID(),'-','')), p.id, 'legacy-refund', p.amount,
       'Refund intent migrated from existing REFUNDING payment', 'PENDING',
       (o.status='REFUNDING' AND EXISTS (
           SELECT 1 FROM showtime_seat s WHERE s.order_id=o.id AND s.status='SOLD')),
       CURRENT_TIMESTAMP(3)
FROM payment_transaction p JOIN orders o ON o.id=p.order_id
WHERE p.status='REFUNDING' AND p.amount > 0;

INSERT INTO payment_task(payment_id,type,status,attempts,next_attempt_at)
SELECT payment_id,'REFUND','PENDING',0,CURRENT_TIMESTAMP(3) FROM payment_refund;

-- Email outbox and delivery log (issue #2): one row per email, written in the caller's transaction.
create table notifications.email_delivery (
    id          uuid        primary key,
    tenant_id   uuid        not null,
    recipient   text        not null,
    template    text        not null,
    subject     text        not null,
    html        text,       -- cleared once SENT: later templates may carry one-time tokens
    status      text        not null check (status in ('PENDING', 'SENDING', 'SENT', 'FAILED')),
    error       text,
    created_at  timestamptz not null,
    sent_at     timestamptz
);
create index email_delivery_tenant_idx on notifications.email_delivery (tenant_id);

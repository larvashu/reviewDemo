-- V1__create_order_table.sql
create table if not exists "ORDERS" (
    "ID"           uuid primary key,
    "AMOUNT"       numeric(8,2) not null,
    "CURRENCY"     char(3)      not null,
    "VAT_AMOUNT"   numeric(8,2),
    "TOTAL_AMOUNT" numeric(8,2)
);

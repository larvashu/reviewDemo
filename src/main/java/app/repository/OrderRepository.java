package app.repository;

import app.model.Order;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static app.jooq.tables.Orders.ORDERS;

/**
 * DAO dla tabeli orders.
 */
public class OrderRepository {

    private static final Logger log = LoggerFactory.getLogger(OrderRepository.class);
    private final DSLContext dsl;

    public OrderRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /* ---------- INSERT ---------- */

    public void insertOrder(Order order) {
        dsl.insertInto(ORDERS)
                .set(ORDERS.ID,        order.id())
                .set(ORDERS.AMOUNT,    order.amount())
                .set(ORDERS.CURRENCY,  order.currency())
                // kolumny VAT są null – wypełni je worker
                .execute();
    }

    /* ---------- SELECT ---------- */

    public Order findOrderById(UUID id) {
        return dsl.selectFrom(ORDERS)
                .where(ORDERS.ID.eq(id))
                .fetchOptional(r -> new Order(
                        r.getId(),
                        r.getAmount(),
                        r.getCurrency()))
                .orElse(null);
    }

    /** NOWA – zwraca rekordy jeszcze nieprzetworzone (VAT_NULL) */
    public List<Order> findUnprocessed() {
        return dsl.selectFrom(ORDERS)
                .where(ORDERS.VAT_AMOUNT.isNull())
                .fetch(r -> new Order(r.getId(), r.getAmount(), r.getCurrency()));
    }

    /** NOWA – zapisuje obliczony VAT + total */
    public void markProcessed(UUID id, BigDecimal vat, BigDecimal total) {
        dsl.update(ORDERS)
                .set(ORDERS.VAT_AMOUNT,   vat)
                .set(ORDERS.TOTAL_AMOUNT, total)
                .where(ORDERS.ID.eq(id))
                .execute();
    }

    /* ---------- CLEANUP ---------- */

    public void truncateOrdersTable() {
        dsl.truncate(ORDERS).restartIdentity().cascade().execute();
    }
}

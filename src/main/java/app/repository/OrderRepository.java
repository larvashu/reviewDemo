package app.repository;

import app.jooq.tables.Orders;
import app.model.Order;
import app.model.ProcessedOrder;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.RecordMapper;

import java.util.List;
import java.util.UUID;

import static app.jooq.tables.Orders.ORDERS;
import static org.jooq.impl.DSL.*;

public class OrderRepository {

    private final DSLContext dsl;

    public OrderRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void insertOrder(Order order) {
        dsl.insertInto(ORDERS)
                .set(ORDERS.ID, order.id())
                .set(ORDERS.AMOUNT, order.amount())
                .set(ORDERS.CURRENCY, order.currency())
                .execute();
    }

    public ProcessedOrder findOrderById(UUID id) {
        return dsl.selectFrom(ORDERS)
                .where(ORDERS.ID.eq(id))
                .fetchOne(new RecordMapper<Record, ProcessedOrder>() {
                    @Override
                    public ProcessedOrder map(Record record) {
                        if (record == null) {
                            return null;
                        }
                        return new ProcessedOrder(
                                record.get(ORDERS.ID),
                                record.get(ORDERS.AMOUNT),
                                record.get(ORDERS.CURRENCY),
                                record.get(ORDERS.VAT_AMOUNT),
                                record.get(ORDERS.TOTAL_AMOUNT)
                        );
                    }
                });
    }

    public int getUnprocessedCount() {
        return dsl.select(count())
                .from(ORDERS)
                .where(ORDERS.VAT_AMOUNT.isNull()) // Zakładamy, że null vat_amount oznacza nieprzetworzone
                .fetchOne(0, int.class);
    }

    public List<Order> findUnprocessed() {
        return dsl.selectFrom(ORDERS)
                .where(ORDERS.VAT_AMOUNT.isNull())
                .limit(10)
                .fetch(new RecordMapper<Record, Order>() {
                    @Override
                    public Order map(Record record) {
                        return new Order(
                                record.get(ORDERS.ID),
                                record.get(ORDERS.AMOUNT),
                                record.get(ORDERS.CURRENCY)
                        );
                    }
                });
    }

    public void truncateOrdersTable() {
        dsl.truncate(ORDERS).restartIdentity().cascade().execute();
    }

    public void updateOrderWithProcessedData(ProcessedOrder processedOrder) {
        dsl.update(ORDERS)
                .set(ORDERS.VAT_AMOUNT, processedOrder.vatAmount())
                .set(ORDERS.TOTAL_AMOUNT, processedOrder.totalAmount())
                .where(ORDERS.ID.eq(processedOrder.id()))
                .execute();
    }

    public void deleteOrdersByIds(List<String> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return;
        }
        dsl.deleteFrom(Orders.ORDERS)
                .where(Orders.ORDERS.ID.in(orderIds))
                .execute();
    }
}
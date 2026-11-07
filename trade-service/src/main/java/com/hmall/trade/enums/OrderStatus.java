package com.hmall.trade.enums;

import lombok.Getter;

@Getter
public enum OrderStatus {
    UNPAID(1, "未付款"),
    PAID(2, "已付款，未发货"),
    CONSIGNED(3, "已发货，未确认"),
    CONFIRMED(4, "确认收货，交易成功"),
    CLOSED(5, "交易取消，订单关闭"),
    FINISHED(6, "交易结束，已评价"),
    ;
    private final int value;
    private final String desc;

    OrderStatus(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public boolean equalsValue(Integer value) {
        if (value == null) {
            return false;
        }
        return getValue() == value;
    }
}

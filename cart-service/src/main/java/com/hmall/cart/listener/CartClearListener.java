package com.hmall.cart.listener;

import com.hmall.cart.service.ICartService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CartClearListener {

    private final ICartService cartService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "cart.clear.queue", durable = "true"),
            exchange = @Exchange(name = "trade.topic", type = ExchangeTypes.TOPIC),
            key = "order.create"
    ))
    public void listenOrderCreate(List<Long> itemIds) {
        // 登录用户信息已由消息头自动写入UserContext，直接清理当前用户的购物车
        cartService.removeByItemIds(itemIds);
    }
}

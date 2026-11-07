package com.hmall.search.listener;

import com.hmall.common.constants.MqConstants;
import com.hmall.search.service.ISearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ItemChangeListener {

    private final ISearchService searchService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "search.item.update.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.ITEM_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.ITEM_UPDATE_KEY
    ))
    public void listenItemUpdate(Long itemId) {
        log.debug("接收到商品更新消息，商品id：{}", itemId);
        searchService.saveItemById(itemId);
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "search.item.delete.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.ITEM_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.ITEM_DELETE_KEY
    ))
    public void listenItemDelete(Long itemId) {
        log.debug("接收到商品删除消息，商品id：{}", itemId);
        searchService.deleteItemById(itemId);
    }
}

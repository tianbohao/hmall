package com.hmall.common.utils;

import com.hmall.common.exception.BizIllegalException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * RabbitMQ 消息发送工具，封装了普通消息、延迟消息、带发送确认的消息
 */
@Slf4j
@RequiredArgsConstructor
public class RabbitMqHelper {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送普通消息
     */
    public void sendMessage(String exchange, String routingKey, Object msg) {
        log.debug("准备发送消息，exchange：{}, routingKey：{}, msg：{}", exchange, routingKey, msg);
        rabbitTemplate.convertAndSend(exchange, routingKey, msg);
    }

    /**
     * 发送延迟消息
     */
    public void sendDelayMessage(String exchange, String routingKey, Object msg, int delay) {
        log.debug("准备发送延迟消息，exchange：{}, routingKey：{}, msg：{}, delay：{}ms", exchange, routingKey, msg, delay);
        rabbitTemplate.convertAndSend(exchange, routingKey, msg, message -> {
            message.getMessageProperties().setDelay(delay);
            return message;
        });
    }

    /**
     * 发送消息并等待Broker确认，发送失败时最多重试 maxRetries 次
     */
    public void sendMessageWithConfirm(String exchange, String routingKey, Object msg, int maxRetries) {
        log.debug("准备发送消息，exchange：{}, routingKey：{}, msg：{}", exchange, routingKey, msg);
        int attempts = Math.max(maxRetries, 1);
        for (int i = 1; i <= attempts; i++) {
            CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());
            try {
                rabbitTemplate.convertAndSend(exchange, routingKey, msg, correlationData);
                CorrelationData.Confirm confirm = correlationData.getFuture().get(5, TimeUnit.SECONDS);
                if (confirm.isAck()) {
                    log.debug("消息发送成功，消息id：{}", correlationData.getId());
                    return;
                }
                log.error("消息发送被拒绝，消息id：{}, 原因：{}, 第{}次尝试",
                        correlationData.getId(), confirm.getReason(), i);
            } catch (Exception e) {
                log.error("消息发送异常，消息id：{}, 第{}次尝试", correlationData.getId(), i, e);
            }
        }
        throw new BizIllegalException("消息发送失败，已达最大重试次数：" + attempts);
    }
}

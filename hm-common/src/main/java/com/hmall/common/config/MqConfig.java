package com.hmall.common.config;

import com.hmall.common.utils.RabbitMqHelper;
import com.hmall.common.utils.UserContext;
import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConditionalOnClass(RabbitTemplate.class)
@AutoConfigureBefore(RabbitAutoConfiguration.class)
public class MqConfig {

    public static final String USER_INFO_HEADER = "user-info";

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        // 发送消息前，自动把当前登录用户写入消息头
        rabbitTemplate.setBeforePublishPostProcessors(message -> {
            Long userId = UserContext.getUser();
            if (userId != null) {
                message.getMessageProperties().setHeader(USER_INFO_HEADER, userId);
            }
            return message;
        });
        return rabbitTemplate;
    }

    @Bean
    public RabbitMqHelper rabbitMqHelper(RabbitTemplate rabbitTemplate) {
        return new RabbitMqHelper(rabbitTemplate);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter messageConverter,
            RabbitProperties rabbitProperties, ObjectProvider<MessageRecoverer> messageRecovererProvider) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        // 接收消息后，自动把消息头中的登录用户放入UserContext
        factory.setAfterReceivePostProcessors(message -> {
            Object userId = message.getMessageProperties().getHeader(USER_INFO_HEADER);
            if (userId != null) {
                UserContext.setUser(Long.valueOf(userId.toString()));
            }
            return message;
        });
        List<Advice> adviceChain = new ArrayList<>();
        // 消费完成后清理UserContext，避免线程复用导致数据串用（最外层，保证重试期间仍能取到用户信息）
        adviceChain.add((MethodInterceptor) invocation -> {
            try {
                return invocation.proceed();
            } finally {
                UserContext.removeUser();
            }
        });
        // 根据配置开启消费重试，重试耗尽后交给MessageRecoverer投递到错误交换机
        RabbitProperties.ListenerRetry retry = rabbitProperties.getListener().getSimple().getRetry();
        if (retry.isEnabled()) {
            RetryInterceptorBuilder.StatelessRetryInterceptorBuilder retryBuilder = RetryInterceptorBuilder.stateless()
                    .maxAttempts(retry.getMaxAttempts())
                    .backOffOptions(retry.getInitialInterval().toMillis(), retry.getMultiplier(),
                            retry.getMaxInterval().toMillis());
            MessageRecoverer messageRecoverer = messageRecovererProvider.getIfAvailable();
            if (messageRecoverer != null) {
                retryBuilder.recoverer(messageRecoverer);
            }
            adviceChain.add(retryBuilder.build());
        }
        factory.setAdviceChain(adviceChain.toArray(new Advice[0]));
        return factory;
    }
}

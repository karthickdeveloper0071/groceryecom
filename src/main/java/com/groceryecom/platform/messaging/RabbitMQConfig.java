package com.groceryecom.platform.messaging;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.groceryecom.shared.AppConstants;

/**
 * RabbitMQ Configuration for async messaging
 */
@Configuration
public class RabbitMQConfig {

    // Exchange
    @Bean
    public TopicExchange ecomExchange() {
        return new TopicExchange("ecom-exchange", true, false);
    }

    // Order Queues
    @Bean
    public Queue orderCreatedQueue() {
        return new Queue(AppConstants.QUEUE_ORDER_CREATED, true);
    }

    @Bean
    public Queue orderConfirmedQueue() {
        return new Queue(AppConstants.QUEUE_ORDER_CONFIRMED, true);
    }

    // Payment Queues
    @Bean
    public Queue paymentProcessedQueue() {
        return new Queue(AppConstants.QUEUE_PAYMENT_PROCESSED, true);
    }

    // Inventory Queues
    @Bean
    public Queue inventoryUpdatedQueue() {
        return new Queue(AppConstants.QUEUE_INVENTORY_UPDATED, true);
    }

    // Notification Queues
    @Bean
    public Queue notificationSendQueue() {
        return new Queue(AppConstants.QUEUE_NOTIFICATION_SEND, true);
    }

    // Vendor Queues
    @Bean
    public Queue vendorRegisteredQueue() {
        return new Queue(AppConstants.QUEUE_VENDOR_REGISTERED, true);
    }

    // Bindings
    @Bean
    public Binding orderCreatedBinding(Queue orderCreatedQueue, TopicExchange ecomExchange) {
        return BindingBuilder.bind(orderCreatedQueue)
                .to(ecomExchange)
                .with("order.created.*");
    }

    @Bean
    public Binding orderConfirmedBinding(Queue orderConfirmedQueue, TopicExchange ecomExchange) {
        return BindingBuilder.bind(orderConfirmedQueue)
                .to(ecomExchange)
                .with("order.confirmed.*");
    }

    @Bean
    public Binding paymentProcessedBinding(Queue paymentProcessedQueue, TopicExchange ecomExchange) {
        return BindingBuilder.bind(paymentProcessedQueue)
                .to(ecomExchange)
                .with("payment.processed.*");
    }

    @Bean
    public Binding inventoryUpdatedBinding(Queue inventoryUpdatedQueue, TopicExchange ecomExchange) {
        return BindingBuilder.bind(inventoryUpdatedQueue)
                .to(ecomExchange)
                .with("inventory.updated.*");
    }

    @Bean
    public Binding notificationSendBinding(Queue notificationSendQueue, TopicExchange ecomExchange) {
        return BindingBuilder.bind(notificationSendQueue)
                .to(ecomExchange)
                .with("notification.send.*");
    }

    @Bean
    public Binding vendorRegisteredBinding(Queue vendorRegisteredQueue, TopicExchange ecomExchange) {
        return BindingBuilder.bind(vendorRegisteredQueue)
                .to(ecomExchange)
                .with("vendor.registered.*");
    }

    // Message Converter
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // RabbitTemplate
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}


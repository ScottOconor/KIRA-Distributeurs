package com.erp.sync;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.postprocessor.GZipPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE    = "erp.sync.exchange";
    public static final String QUEUE       = "erp.sync.events";
    public static final String ROUTING_KEY = "erp.sync.#";

    @Bean TopicExchange syncExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    // Les arguments doivent correspondre exactement à la déclaration du hub (RabbitMQConfig
    // côté hub-backend) — sinon RabbitMQ rejette la connexion avec PRECONDITION_FAILED dès
    // qu'un spoke tente de déclarer la même queue avec des arguments différents.
    @Bean Queue syncQueue() {
        return QueueBuilder.durable(QUEUE)
                .withArgument("x-dead-letter-exchange", EXCHANGE + ".dlx")
                .withArgument("x-dead-letter-routing-key", QUEUE)
                .build();
    }

    @Bean Binding syncBinding(Queue syncQueue, TopicExchange syncExchange) {
        return BindingBuilder.bind(syncQueue).to(syncExchange).with(ROUTING_KEY);
    }

    @Bean Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean RabbitTemplate rabbitTemplate(ConnectionFactory cf, Jackson2JsonMessageConverter mc) {
        RabbitTemplate tpl = new RabbitTemplate(cf);
        tpl.setMessageConverter(mc);
        // Compresse chaque message sortant (en-tête content-encoding: gzip) — le hub le décompresse
        // déjà automatiquement côté réception (DelegatingDecompressingPostProcessor, RabbitMQConfig
        // côté hub-backend), mais ce post-processor manquait ici côté spoke. Sans lui, un
        // FULL_SNAPSHOT un peu volumineux (ex: 29 Mo pour une société avec beaucoup d'historique)
        // dépasse la limite de taille de message du broker (16 Mo) et échoue indéfiniment à chaque
        // tentative puisque la taille ne change jamais entre deux retries. Le JSON compresse
        // typiquement 5-8x, ce qui passe largement sous la limite.
        tpl.setBeforePublishPostProcessors(new GZipPostProcessor());
        return tpl;
    }
}

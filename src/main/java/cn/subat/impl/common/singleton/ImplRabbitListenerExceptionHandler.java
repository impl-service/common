package cn.subat.impl.common.singleton;

import cn.subat.impl.common.dto.ImplResponse;
import com.rabbitmq.client.AMQP;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.json.JsonMapper;
import io.micronaut.rabbitmq.bind.RabbitConsumerState;
import io.micronaut.rabbitmq.exception.DefaultRabbitListenerExceptionHandler;
import io.micronaut.rabbitmq.exception.RabbitListenerException;
import io.micronaut.rabbitmq.exception.RabbitListenerExceptionHandler;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Optional;


@Replaces(DefaultRabbitListenerExceptionHandler.class)
@Singleton
@Slf4j
public class ImplRabbitListenerExceptionHandler implements RabbitListenerExceptionHandler {


    private final JsonMapper jsonMapper;

    public ImplRabbitListenerExceptionHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }


    @Override
    public void handle(RabbitListenerException exception) {
        log.error(exception.getLocalizedMessage());
        Optional<RabbitConsumerState> rabbitConsumerStateOpt = exception.getMessageState();
        rabbitConsumerStateOpt.ifPresent(messageState -> {
            AMQP.BasicProperties basicProperties = new AMQP.BasicProperties.Builder()
                    .correlationId(messageState.getProperties().getCorrelationId())
                    .build();
            try {
                byte[] body = jsonMapper.writeValueAsBytes(ImplResponse.of(-500, "Service Error:"+exception.getLocalizedMessage()));
                if (messageState.getProperties().getReplyTo() != null){
                    messageState.getChannel().basicPublish("",messageState.getProperties().getReplyTo(),basicProperties,body);
                }else{
                    log.error(exception.getLocalizedMessage());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        exception.printStackTrace();
    }
}

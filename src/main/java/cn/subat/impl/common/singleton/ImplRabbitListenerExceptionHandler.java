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

import java.io.IOException;
import java.util.Optional;


@Replaces(DefaultRabbitListenerExceptionHandler.class)
@Singleton
public class ImplRabbitListenerExceptionHandler implements RabbitListenerExceptionHandler {


    private final JsonMapper jsonMapper;

    public ImplRabbitListenerExceptionHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }


    @Override
    public void handle(RabbitListenerException exception) {
        Optional<RabbitConsumerState> rabbitConsumerStateOpt = exception.getMessageState();
        rabbitConsumerStateOpt.ifPresent(messageState -> {
            AMQP.BasicProperties basicProperties = new AMQP.BasicProperties.Builder()
                    .correlationId(messageState.getProperties().getCorrelationId())
                    .build();
            try {
                byte[] body = jsonMapper.writeValueAsBytes(ImplResponse.of(-500, "Service Error:"+exception.getLocalizedMessage()));
                messageState.getChannel().basicPublish("",messageState.getProperties().getReplyTo(),basicProperties,body);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        exception.printStackTrace();
    }
}

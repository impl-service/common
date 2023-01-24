package cn.subat.impl.common.singleton;

import cn.subat.impl.common.dto.ImplResponse;
import cn.subat.impl.common.util.ImplCamelToSnake;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.rabbitmq.client.AMQP;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.annotation.Body;
import io.micronaut.json.JsonMapper;
import io.micronaut.rabbitmq.bind.RabbitBodyBinder;
import io.micronaut.rabbitmq.bind.RabbitConsumerState;
import io.micronaut.rabbitmq.serdes.JavaLangRabbitMessageSerDes;
import io.micronaut.rabbitmq.serdes.RabbitMessageSerDesRegistry;
import io.micronaut.validation.validator.Validator;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.NonNull;

import javax.validation.ValidationException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Replaces(RabbitBodyBinder.class)
@Singleton
public class ImplRabbitBodyBinder extends RabbitBodyBinder{

    private RabbitMessageSerDesRegistry serDesRegistry;

    @Inject
    Validator validator;
    @Inject
    JavaLangRabbitMessageSerDes langRabbitMessageSerDes;

    private final JsonMapper jsonMapper;

    /**
     * Default constructor.
     *  @param serDesRegistry The registry to get a deserializer
     * @param jsonMapper json mapper
     */
    public ImplRabbitBodyBinder(RabbitMessageSerDesRegistry serDesRegistry, JsonMapper jsonMapper) {
        super(serDesRegistry);
        this.serDesRegistry = serDesRegistry;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public BindingResult<Object> bind(ArgumentConversionContext<Object> context, RabbitConsumerState messageState) {

        Argument<Object> bodyType = context.getArgument();
        Optional<Object> message = Optional.empty();
        if(bodyType.getAnnotation(Body.class) == null){
            messageState = getRabbitConsumerState(messageState, bodyType);
        }

        if(serDesRegistry.findSerdes(bodyType).isPresent()){
            try {
                message = Optional.ofNullable(serDesRegistry.findSerdes(bodyType).get().deserialize(messageState,bodyType));
            }catch (Exception e){
                String errMsg = "["+bodyType.getName()+"] "+e.getLocalizedMessage();
                onError(messageState,errMsg);
                throw new ValidationException(errMsg);
            }
        }
        Optional<Object> finalMessage = message;
        return () -> finalMessage;
    }

    @NonNull
    private RabbitConsumerState getRabbitConsumerState(RabbitConsumerState messageState, Argument<Object> bodyType) {
        JsonElement jsonObject = JsonParser.parseString(new String(messageState.getBody()));
        if(!jsonObject.isJsonObject()){
            String errMsg = "json body required";
            onError(messageState,errMsg);
            throw new ValidationException(errMsg);
        }
        JsonElement element = jsonObject.getAsJsonObject().get(ImplCamelToSnake.camelToSnake(bodyType.getName()));
        if(element != null){
            try {
                byte[] body = parseElement(element, bodyType, bodyType.getType().getAnnotation(Nullable.class) == null);
                messageState = new RabbitConsumerState(messageState.getEnvelope(), messageState.getProperties(),body, messageState.getChannel());
            }catch (Exception e){
                String errMsg = "["+ ImplCamelToSnake.camelToSnake(bodyType.getName())+"] "+e.getLocalizedMessage();
                onError(messageState,errMsg);
                throw new ValidationException(errMsg);
            }
        }else if(bodyType.getType().getAnnotation(Nullable.class) == null){
            String errMsg = "["+ ImplCamelToSnake.camelToSnake(bodyType.getName())+"] is required";
            onError(messageState,errMsg);
            throw new ValidationException(errMsg);
        }else {
            byte[] body = "".getBytes(StandardCharsets.UTF_8);
            messageState = new RabbitConsumerState(messageState.getEnvelope(), messageState.getProperties(),body, messageState.getChannel());
        }
        return messageState;
    }

    public void onError(RabbitConsumerState messageState,String msg){
        OnError(messageState, msg, jsonMapper);
    }


    private byte[] parseElement(JsonElement element, Argument<Object> bodyType, boolean notNull){
        byte[] body = null;

        switch (bodyType.getSimpleName()){
            case "Long": {
                try {
                    body = langRabbitMessageSerDes.serialize(element.getAsLong(), null);
                }catch (Exception e){
                    throw new ValidationException("must be integer");
                }
                break;
            }
            case "Integer":{
                try {
                    body = langRabbitMessageSerDes.serialize(element.getAsInt(),null);
                }catch (Exception e){
                    throw new ValidationException("must be integer");
                }
                break;
            }
            case "String":{
                if(notNull && element.getAsString().length() == 0){
                    throw new ValidationException("can not be empty");
                }
                body = langRabbitMessageSerDes.serialize(element.getAsString(),null);
                break;
            }
            default:{
                body = langRabbitMessageSerDes.serialize(element.toString(),null);
            }
        }
        return body;
    }

    public static void OnError(RabbitConsumerState messageState, String msg, JsonMapper jsonMapper) {
        AMQP.BasicProperties basicProperties = new AMQP.BasicProperties.Builder()
                .correlationId(messageState.getProperties().getCorrelationId())
                .build();
        try {
            byte[] serialized = jsonMapper.writeValueAsBytes(ImplResponse.builder()
                    .rc(-1002)
                    .msg(msg)
                    .build()
            );
            messageState.getChannel().basicPublish("",messageState.getProperties().getReplyTo(),basicProperties,serialized);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}

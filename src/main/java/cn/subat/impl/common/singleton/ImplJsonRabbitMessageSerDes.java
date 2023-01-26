package cn.subat.impl.common.singleton;

import cn.subat.impl.common.dto.ImplResponse;
import cn.subat.impl.common.util.ImplCamelToSnake;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.serialize.exceptions.SerializationException;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.json.JsonMapper;
import io.micronaut.rabbitmq.bind.RabbitConsumerState;
import io.micronaut.rabbitmq.intercept.MutableBasicProperties;
import io.micronaut.rabbitmq.serdes.JsonRabbitMessageSerDes;
import io.micronaut.validation.validator.Validator;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import javax.validation.ConstraintViolation;
import javax.validation.ValidationException;
import java.io.IOException;
import java.util.Set;

@Replaces(JsonRabbitMessageSerDes.class)
@Singleton
public class ImplJsonRabbitMessageSerDes extends JsonRabbitMessageSerDes {

    private final JsonMapper jsonMapper;
    @Inject
    Validator validator;

    public ImplJsonRabbitMessageSerDes(JsonMapper jsonMapper) {
        super(jsonMapper);
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Object deserialize(RabbitConsumerState messageState, Argument<Object> type) {
        byte[] body = messageState.getBody();
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            Object obj = jsonMapper.readValue(body,type);
            validateParam(messageState,obj,type);
            return obj;
        } catch (IOException e) {
            onError(messageState,e.getLocalizedMessage());
            throw new SerializationException("Error decoding JSON stream for type [" + type.getName() + "]: " + e.getMessage());
        }
    }

    private void validateParam(RabbitConsumerState messageState,Object obj,Argument<Object> type){
        if(obj == null){
            onError(messageState,"body invalid");
            throw new SerializationException("body invalid");
        }
        Set<ConstraintViolation<Object>> constraintViolations = validator.validate(obj);
        if(!constraintViolations.isEmpty()){
            StringBuilder stringBuilder = new StringBuilder();
            int index = 0;
            for (ConstraintViolation<Object> constraintViolation:constraintViolations){
                if(index == 0){
                    stringBuilder.append(constraintViolation.getMessageTemplate().startsWith("{javax")? ImplCamelToSnake.camelToSnake(constraintViolation.getPropertyPath().toString()):"")
                            .append(" ")
                            .append(constraintViolation.getMessage());
                }
                index++;
            }
            onError(messageState,stringBuilder.toString());
            throw new ValidationException(stringBuilder.toString());
        }
    }

    public void onError(RabbitConsumerState messageState,String msg){
        ImplRabbitBodyBinder.OnError(messageState, msg, jsonMapper);
    }

    @Override
    public byte[] serialize(Object data, MutableBasicProperties basicProperties) {

        if (data == null) {
            return null;
        }
        try {
            byte[] serialized = jsonMapper.writeValueAsBytes(data);
            if (serialized != null && basicProperties.getContentType() == null) {
                basicProperties.setContentType(MediaType.APPLICATION_JSON);
            }
            return serialized;
        } catch (IOException e) {
            try {
                return jsonMapper.writeValueAsBytes(new ImplResponse<>(-1, e.getLocalizedMessage()));
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            throw new SerializationException("Error encoding object [" + data + "] to JSON: " + e.getMessage());
        }
    }
}

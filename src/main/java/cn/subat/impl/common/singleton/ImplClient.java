package cn.subat.impl.common.singleton;

import cn.subat.impl.common.dto.ImplResponse;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.rabbitmq.client.AMQP;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.rabbitmq.reactive.RabbitPublishState;
import io.micronaut.rabbitmq.reactive.ReactivePublisher;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
@Slf4j
public class ImplClient {

    ReactivePublisher reactivePublisher;
    JsonMapper jsonMapper;

    public ImplClient(ReactivePublisher reactivePublisher,JsonMapper jsonMapper){
        this.reactivePublisher = reactivePublisher;
        this.jsonMapper = jsonMapper;
    }

    public Mono<String> rpc(String api){
        Map<String, Object> bodyMap = new java.util.HashMap<>();
        Map<String, Object> header = new java.util.HashMap<>();
        return rpc(api, bodyMap, header);
    }

    public Mono<String> rpc(String api, Map<String,? extends Object> bodyMap){
        Map<String, Object> header = new java.util.HashMap<>();
        return rpc(api,bodyMap, header);
    }

    public Mono<String> rpc(String api, Map<String,? extends Object> bodyMap, Map<String,Object> header){
        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder().headers(header).replyTo("amq.rabbitmq.reply-to").build();
        byte[] body = new Gson().toJson(bodyMap).getBytes(StandardCharsets.UTF_8);
        RabbitPublishState state = new RabbitPublishState(ImplChannel.ApiExchangeName,api,properties,body);
        return Mono.from(reactivePublisher.publishAndReply(state)).flatMap(rabbitConsumerState -> {
            JsonElement res = JsonParser.parseString(new String(rabbitConsumerState.getBody()));
            return Mono.just(new Gson().toJson(res));
        });

    }

    public <T> ImplResponse<T> rpcAs(Class<T> tClass,String api, Map<String,? extends Object> bodyMap, Map<String,Object> header) throws IOException {
        String res = rpc(api,bodyMap,header).block();
        assert res != null;
        return jsonMapper.readValue(res, Argument.of(ImplResponse.class,tClass));
    }

    public <T> ImplResponse<List<T>> rpcAsList(Class<T> tClass, String api, Map<String,? extends Object> bodyMap, Map<String,Object> header) throws IOException {
        String res = rpc(api,bodyMap,header).block();
        assert res != null;
        return jsonMapper.readValue(res, Argument.of(ImplResponse.class,Argument.of(List.class,tClass)));
    }

    public void publishTopicMessage(String topic, Map<String,Object> bodyMap){
        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder().build();
        byte[] body = new Gson().toJson(bodyMap).getBytes(StandardCharsets.UTF_8);
        RabbitPublishState state = new RabbitPublishState(ImplChannel.TopicExchangeName,topic,properties,body);
        Mono.from(reactivePublisher.publish(state)).subscribe();
    }

    public void publishDelayMessage(Duration delay, String queue, Map<String,Object> bodyMap){
        HashMap<String,Object> body = new HashMap<>(bodyMap);
        body.put("x-delay",delay.toMillis());
        publishMessage(queue,body);
    }

    public void publishPriorityMessage(int priority, String queue, Map<String,Object> bodyMap){
        HashMap<String,Object> body = new HashMap<>(bodyMap);
        body.put("x-priority",priority);
        publishMessage(queue,body);
    }

    public void publishMessage(String queue, Map<String,Object> bodyMap){
        Map<String,Object> header = new HashMap<>();
        AMQP.BasicProperties.Builder properties = new AMQP.BasicProperties.Builder();

        // 设置优先级
        if (bodyMap.containsKey("x-priority")){
            properties.priority((Integer) bodyMap.get("x-priority"));
        }
        // 设置延时任务
        if (bodyMap.containsKey("x-delay")){
            header.put("x-delay",bodyMap.get("x-delay"));
        }
        properties.headers(header);
        byte[] body = new Gson().toJson(bodyMap).getBytes(StandardCharsets.UTF_8);
        RabbitPublishState state = new RabbitPublishState(ImplChannel.QueueExchangeName,queue,properties.build(),body);
        Mono.from(reactivePublisher.publish(state)).subscribe();
    }
}

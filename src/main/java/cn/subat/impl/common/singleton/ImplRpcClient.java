package cn.subat.impl.common.singleton;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.rabbitmq.client.AMQP;
import io.micronaut.json.JsonMapper;
import io.micronaut.rabbitmq.reactive.RabbitPublishState;
import io.micronaut.rabbitmq.reactive.ReactivePublisher;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;

@Singleton
@Slf4j
public class ImplRpcClient {

    ReactivePublisher reactivePublisher;
    private final Scheduler scheduler;


    public ImplRpcClient(ReactivePublisher reactivePublisher, @Named(TaskExecutors.IO) ExecutorService executorService, JsonMapper jsonMapper) {
        this.reactivePublisher = reactivePublisher;
        this.scheduler = Schedulers.fromExecutor(executorService);
    }


    public <T> Mono<T> getAs(String api,Class<T> type){
        return getAs(api,Map.of(),type);
    }


    public <T> Mono<T> getAs(String api,Map<String,Object> bodyMap,Class<T> type){
        return get(api,bodyMap,Map.of()).map(s -> new Gson().fromJson(s,type));
    }

    public Mono<String> get(String api){
        return get(api,Map.of(),Map.of());
    }

    public Mono<String> get(String api, Map<String,? extends Object> bodyMap){
        return get(api,bodyMap,Map.of());
    }

    public Mono<String> get(String api, Map<String,? extends Object> bodyMap,Map<String,Object> header){
        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder().headers(header).replyTo("amq.rabbitmq.reply-to").build();
        byte[] body = new Gson().toJson(bodyMap).getBytes(StandardCharsets.UTF_8);
        RabbitPublishState state = new RabbitPublishState(ImplChannel.ApiExchangeName,api,properties,body);
        return Mono.from(reactivePublisher.publishAndReply(state)).flatMap(rabbitConsumerState -> {
            JsonElement res = JsonParser.parseString(new String(rabbitConsumerState.getBody()));
            return Mono.just(new Gson().toJson(res));
        });

    }

    public void publishTopic(String topic,Map<String,Object> bodyMap){
        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder().build();
        byte[] body = new Gson().toJson(bodyMap).getBytes(StandardCharsets.UTF_8);
        RabbitPublishState state = new RabbitPublishState(ImplChannel.TopicExchangeName,topic,properties,body);
        Mono.from(reactivePublisher.publish(state)).subscribe();
    }
}

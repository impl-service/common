package cn.subat.impl.common.singleton;

import cn.subat.impl.common.service.StartService;
import com.rabbitmq.client.Channel;
import io.micronaut.context.ApplicationContext;
import io.micronaut.rabbitmq.connect.ChannelInitializer;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Duration;

@Slf4j
@Getter
@Singleton
public class ImplChannel extends ChannelInitializer {

    public final static String ApiExchangeName = "impl-service";
    public final static String TopicExchangeName = "impl-topic";
    public final static String QueueExchangeName = "impl-queue";

    ApplicationContext context;

    public ImplChannel(ApplicationContext context) {
        this.context = context;
    }
    @Override
    public void initialize(Channel channel, String name) throws IOException {
        StartService startService = new StartService(context);
        startService.registerChannel(channel);
    }
}

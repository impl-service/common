package cn.subat.impl.common.singleton;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.rabbitmq.bind.RabbitPropertyBinder;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Replaces(RabbitPropertyBinder.class)
@Singleton
@Slf4j
public class ImplRabbitPropertyBinder extends RabbitPropertyBinder{
    /**
     * Default constructor.
     *
     * @param conversionService The conversion service to convert the body
     */
    public ImplRabbitPropertyBinder(ConversionService conversionService) {
        super(conversionService);
    }

    @Override
    public boolean supports(ArgumentConversionContext<Object> context) {
        String[] ignore = {"appId", "userId"};
        List<String> ignoreList = new ArrayList<>(Arrays.asList(ignore));
        if (ignoreList.contains(context.getArgument().getName())){
            return false;
        }
        return super.supports(context);
    }
}

package cn.subat.impl.common.service;

import cn.subat.impl.common.config.ImplConfig;
import cn.subat.impl.common.dto.ImplResponse;
import cn.subat.impl.common.dto.ImplSettingDto;
import cn.subat.impl.common.singleton.ImplClient;
import cn.subat.impl.common.util.ImplCamelToSnake;
import cn.subat.impl.spdoc.annotation.SPDocField;
import com.google.gson.Gson;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Bean;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.*;

import static cn.subat.impl.common.singleton.ImplChannel.*;


@Slf4j
@Bean
public class StartService {

    private JsonMapper jsonMapper;
    private ImplConfig config;
    private ImplClient implClient;
    private final ApplicationContext context;
    private Channel channel;

    public StartService(ApplicationContext context){
        this.context = context;
    }

    public void createBean(){
        this.config = context.getBean(ImplConfig.class);
        this.implClient = context.getBean(ImplClient.class);
        this.jsonMapper = context.getBean(JsonMapper.class);
    }
    public void start() {
        createBean();
        registerConfig();
        readConfig();
        registerApiDoc();
    }

    public void registerChannel(Channel channel) throws IOException {
        this.channel = channel;
        channel.exchangeDeclare(ApiExchangeName, BuiltinExchangeType.DIRECT, true);
        channel.exchangeDeclare(TopicExchangeName, BuiltinExchangeType.TOPIC, true);

        Map<String, Object> args = new HashMap<>();
        args.put("x-delayed-type", "direct");
        channel.exchangeDeclare(QueueExchangeName, "x-delayed-message", true, false, args);

        registerTopicQueue();
        registerNormalQueue();
        registerApiQueue();
        Mono.fromRunnable(this::start).delaySubscription(Duration.ofSeconds(1)).subscribe();
    }

    private void registerTopicQueue() throws IOException {
        LinkedHashMap<String,Object> apiDoc = readApiDoc();
        Object topics = apiDoc.get("topics");
        if(topics instanceof LinkedHashMap){
            LinkedHashMap<String,Object> topicsMap = (LinkedHashMap<String, Object>) topics;
            for(String path:topicsMap.keySet()){
                channel.queueDeclare(path,true,false,false,null);
                channel.queueBind(path, TopicExchangeName, topicsMap.get(path).toString());
            }
            log.info("主题注册完成,总共：{}条",topicsMap.keySet().size());
        }
    }

    private void registerNormalQueue() throws IOException {
        LinkedHashMap<String,Object> apiDoc = readApiDoc();
        Object queues = apiDoc.get("queues");
        if(queues instanceof LinkedHashMap){
            Map<String, Object> args = new HashMap<>();
            args.put("x-max-priority", 10);
            LinkedHashMap<String,Object> queuesMap = (LinkedHashMap<String, Object>) queues;
            for(String path:queuesMap.keySet()){
                channel.queueDeclare(path,true,false,false,args);
                channel.queueBind(path, QueueExchangeName, path);
            }
            log.info("队列注册完成,总共：{}条",queuesMap.keySet().size());
        }
    }

    private void registerApiQueue() throws IOException {

        LinkedHashMap<String,Object> apiDoc = readApiDoc();
        Object paths = apiDoc.get("paths");
        LinkedHashMap<?,?> info = (LinkedHashMap<?, ?>) apiDoc.get("info");
        String key = (String) info.get("key");
        String title = (String) info.get("title");

        if(paths instanceof LinkedHashMap){
            LinkedHashMap<?,?> pathsMap = (LinkedHashMap<?,?>) paths;
            ArrayList<Map<String,Object>> apiMap = new ArrayList<>();
            for(Object path:pathsMap.keySet()){
                String queueName = path.toString().replace("/","");
                channel.queueDeclare(queueName,true,false,false,null);
                channel.queueBind(queueName, ApiExchangeName,queueName);

                String summary = getSummary(pathsMap, path, queueName);
                String tag = getTag(pathsMap, path, queueName);
                Map<String, Object> api = new java.util.HashMap<>();
                api.put("api", queueName);
                api.put("name", summary);
                api.put("service", key);
                api.put("service_title", title);
                api.put("tag", tag);
                apiMap.add(api);
            }
            Mono.fromRunnable(()-> registerApi(apiMap,key))
                    .subscribeOn(Schedulers.boundedElastic())
                    .delaySubscription(Duration.ofSeconds(1))
                    .subscribe();
        }
    }

    private String getSummary(LinkedHashMap<?, ?> pathsMap, Object path, String queueName) {
        String summary = queueName;
        LinkedHashMap<?,?> pathInfo = (LinkedHashMap<?, ?>) pathsMap.get(path);
        if(pathInfo.get("post") instanceof LinkedHashMap){
            LinkedHashMap<?,?> postInfo = (LinkedHashMap<?, ?>) pathInfo.get("post");
            if(postInfo.get("summary") instanceof String){
                summary = postInfo.get("summary").toString();
            }
        }
        if(pathInfo.get("get") instanceof LinkedHashMap){
            LinkedHashMap<?,?> getInfo = (LinkedHashMap<?, ?>) pathInfo.get("get");
            if(getInfo.get("summary") instanceof String){
                summary = getInfo.get("summary").toString();
            }
        }
        return summary;
    }

    private String getTag(LinkedHashMap<?, ?> pathsMap, Object path, String queueName) {
        String tag = queueName;
        LinkedHashMap<?,?> pathInfo = (LinkedHashMap<?, ?>) pathsMap.get(path);
        if(pathInfo.get("post") instanceof LinkedHashMap){
            LinkedHashMap<?,?> postInfo = (LinkedHashMap<?, ?>) pathInfo.get("post");
            if(postInfo.get("tags") instanceof ArrayList){
                ArrayList<?> tags = (ArrayList<?>) postInfo.get("tags");
                if(tags.size()>0){
                    tag = tags.get(0).toString();
                }
            }
        }
        if(pathInfo.get("get") instanceof LinkedHashMap){
            LinkedHashMap<?,?> getInfo = (LinkedHashMap<?, ?>) pathInfo.get("get");
            if(getInfo.get("tags") instanceof ArrayList){
                ArrayList<?> tags = (ArrayList<?>) getInfo.get("tags");
                if(tags.size()>0){
                    tag = tags.get(0).toString();
                }
            }
        }
        return tag;
    }

    public void registerConfig(){
        log.info("开始注册配置:{}",config.getClass().toString());
        Flux.just(config.getClass().getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(SPDocField.class))
                .map(this::getSettingInfo)
                .flatMap(map-> implClient.rpc("core.setting.create",map))
                .subscribe(s -> log.info("注册配置已完成:{}",s));
    }

    /**
     * 获取配置信息
     * @param field 配置字段
     * @return 配置信息
     */
    private Map<String, String> getSettingInfo(Field field) {
        log.info("注册配置:{}",field.getName());
        Map<String, String> stringMap = new java.util.HashMap<>();
        stringMap.put("app_key", config.getAppKey());
        stringMap.put("item", ImplCamelToSnake.camelToSnake(field.getName()));
        stringMap.put("comment", field.getAnnotation(SPDocField.class).value());
        return stringMap;
    }

    public void readConfig(){
        String appKey = config.getAppKey();
        Map<String, String> bodyMap = new java.util.HashMap<>();
        bodyMap.put("app_key", appKey);
        implClient.rpcAsList(ImplSettingDto.class,"core.setting.app.read",bodyMap,new HashMap<>())
                .filter(implResponse -> implResponse.getRc() == 1)
                .map(ImplResponse::getData)
                .flatMapIterable(settingDtoList -> settingDtoList)
                .map(this::setConfigValue)
                .delaySubscription(Duration.ofSeconds(2))
                .doOnComplete(()->log.info("配置读取完成{}",config))
                .doOnError(e->log.error("配置读取失败",e))
                .subscribe();
    }

    /**
     * 注册接口
     */
    public void registerApi(ArrayList<Map<String,Object>> list, String service){
        if(!context.containsBean(ImplClient.class)) return;
        ImplClient rpcClient = context.getBean(ImplClient.class);
        Map<String, java.io.Serializable> bodyMap = new java.util.HashMap<>();
        bodyMap.put("service", service);
        bodyMap.put("api_list", list);
        rpcClient.rpc("core.api.register", bodyMap).map(r->{
            log.info("接口注册已完成:{}",r);
            return r;
        }).subscribe();
    }

    /**
     * 注册接口文档
     */
    public void registerApiDoc(){
        Object paths = readApiDoc().get("paths");
        ImplClient rpcClient = context.getBean(ImplClient.class);
        Map<String, String> bodyMap = new java.util.HashMap<>();
        bodyMap.put("api_doc", new Gson().toJson(paths));
        rpcClient.rpc("core.api.doc.register", bodyMap).map(r->{
            log.info("接口文档注册已完成:{}",r);
            return r;
        }).subscribe();
    }


    /**
     * 设置当前配置值
     * @param settingDto 配置信息
     * @return 配置信息
     */
    private ImplSettingDto setConfigValue(ImplSettingDto settingDto){
        for (Field field : config.getClass().getDeclaredFields()) {
            if(field.getName().equals(ImplCamelToSnake.snakeToCamel(settingDto.getItem()))){
                field.setAccessible(true);
                try {
                    field.set(config, settingDto.getValue());
                } catch (IllegalAccessException e) {
                    log.error("配置读取失败",e);
                }
            }
        }
        return settingDto;
    }

    private LinkedHashMap<String,Object> readApiDoc(){
        InputStream schemaIS = this.getClass().getClassLoader().getResourceAsStream("spdoc/api.yaml");
        Yaml yaml = new Yaml();
        return yaml.load(schemaIS);
    }
}

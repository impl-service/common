package cn.subat.impl.common.service;

import cn.subat.impl.common.config.ImplConfig;
import cn.subat.impl.common.dto.ImplResponse;
import cn.subat.impl.common.dto.ImplSettingDto;
import cn.subat.impl.common.singleton.ImplRpcClient;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.subat.impl.common.singleton.ImplChannel.ApiExchangeName;
import static cn.subat.impl.common.singleton.ImplChannel.TopicExchangeName;


@Slf4j
@Bean
public class StartService {

    private final JsonMapper jsonMapper;
    private final ImplConfig config;
    private final ImplRpcClient implRpcClient;
    private final ApplicationContext context;
    private Channel channel;

    public StartService(ApplicationContext context) {
        this.context = context;
        this.config = context.getBean(ImplConfig.class);
        this.implRpcClient = context.getBean(ImplRpcClient.class);
        this.jsonMapper = context.getBean(JsonMapper.class);
    }

    public void init(Channel channel) throws IOException {
        this.channel = channel;
        registerChannel();
        registerConfig();
        readConfig();
        registerApiDoc();
    }

    public void registerChannel() throws IOException {
        channel.exchangeDeclare(ApiExchangeName, BuiltinExchangeType.DIRECT, true);
        channel.exchangeDeclare(TopicExchangeName, BuiltinExchangeType.FANOUT, true);
        registerTopicQueue();
        registerApiQueue();
    }


    private void registerTopicQueue(){
        if (context.containsBean(ImplConfig.class)){
            ImplConfig config = context.getBean(ImplConfig.class);
            List<String> topics = config.getTopics();
            for (String topic : topics) {
                if (!topic.contains("topic.")) continue;
                try {
                    channel.queueDeclare(topic,true,false,false,null);
                    channel.queueBind(topic,TopicExchangeName,topic.split("topic\\.")[1]);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            log.info("主题注册完成,总共：{}条",topics.size());
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
            registerApi(apiMap,key);
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
        Flux.just(config.getClass().getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(SPDocField.class))
                .map(this::getSettingInfo)
                .flatMap(map->implRpcClient.get("core.setting.create",map))
                .subscribe(s -> log.info("注册配置已完成:{}",s));
    }

    /**
     * 获取配置信息
     * @param field 配置字段
     * @return 配置信息
     */
    private Map<String, String> getSettingInfo(Field field) {
        Map<String, String> stringMap = new java.util.HashMap<>();
        stringMap.put("app_key", config.getAppKey());
        stringMap.put("item", camelToSnake(field.getName()));
        stringMap.put("comment", field.getAnnotation(SPDocField.class).value());
        return stringMap;
    }

    public void readConfig(){
        String appKey = config.getAppKey();
        Map<String, String> bodyMap = new java.util.HashMap<>();
        bodyMap.put("app_key", appKey);
        implRpcClient.get("core.setting.app.read", bodyMap)
                .map(this::parseResponse)
                .filter(implResponse -> implResponse.getRc() == 1)
                .map(ImplResponse::getData)
                .flatMapIterable(settingDtoList -> settingDtoList)
                .map(this::setConfigValue)
                .delaySubscription(Duration.ofSeconds(2))
                .doOnComplete(()->log.info("配置读取完成{}",config))
                .subscribe();
    }

    /**
     * 注册接口
     */
    public void registerApi(ArrayList<Map<String,Object>> list, String service){
        ImplRpcClient rpcClient = context.getBean(ImplRpcClient.class);
        Map<String, java.io.Serializable> bodyMap = new java.util.HashMap<>();
        bodyMap.put("service", service);
        bodyMap.put("api_list", list);
        rpcClient.get("core.api.register", bodyMap).map(r->{
            log.info("接口注册已完成:{}",r);
            return r;
        }).subscribe();
    }

    /**
     * 注册接口文档
     */
    public void registerApiDoc(){
        Object paths = readApiDoc().get("paths");
        ImplRpcClient rpcClient = context.getBean(ImplRpcClient.class);
        Map<String, String> bodyMap = new java.util.HashMap<>();
        bodyMap.put("api_doc", new Gson().toJson(paths));
        rpcClient.get("core.api.doc.register", bodyMap).map(r->{
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
        String setMethodName = "set"+snakeToCamel(settingDto.getItem()).substring(0,1).toUpperCase()+snakeToCamel(settingDto.getItem()).substring(1);
        try {
            config.getClass().getDeclaredMethod(setMethodName, String.class).invoke(config, settingDto.getValue());
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        return settingDto;
    }


    /**
     * 解析返回结果
     * @param s 返回结果
     * @return 返回结果
     */
    @SuppressWarnings("unchecked")
    private ImplResponse<List<ImplSettingDto>> parseResponse(String s) {
        ImplResponse response;
        try {
            response = jsonMapper.readValue(s, Argument.of(ImplResponse.class));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        List<ImplSettingDto> settingList;
        try {
            settingList = jsonMapper.readValue(new Gson().toJson(response.getData()),Argument.listOf(ImplSettingDto.class));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        response.setData(settingList);
        return response;
    }


    private String snakeToCamel(String str){
        StringBuilder sb = new StringBuilder();
        String[] split = str.split("\\.");
        for (int i = 0; i < split.length; i++) {
            if(i==0){
                sb.append(split[i]);
            }else{
                sb.append(split[i].substring(0,1).toUpperCase()).append(split[i].substring(1));
            }
        }
        return sb.toString();
    }

    private String camelToSnake(String str){
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            if(Character.isUpperCase(str.charAt(i))){
                sb.append(".").append(Character.toLowerCase(str.charAt(i)));
            }else{
                sb.append(str.charAt(i));
            }
        }
        return sb.toString();
    }

    private LinkedHashMap<String,Object> readApiDoc(){
        InputStream schemaIS = this.getClass().getClassLoader().getResourceAsStream("spdoc/api.yaml");
        Yaml yaml = new Yaml();
        return yaml.load(schemaIS);
    }
}

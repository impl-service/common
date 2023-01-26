package cn.subat.impl.common.singleton;

import cn.subat.impl.common.dto.ImplResponse;
import com.google.gson.Gson;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Singleton
@Slf4j
@SuppressWarnings({"unchecked", "rawtypes"})
public class ImplResponseSerde implements Deserializer<ImplResponse> {

    @Inject
    private JsonMapper jsonMapper;
    @Nullable
    @Override
    public ImplResponse deserialize(Decoder decoder, DecoderContext context, Argument<? super ImplResponse> type) throws IOException {
        Decoder decoderObject = decoder.decodeObject(type);
        ImplResponse response = new ImplResponse<>();
        while (true){
            String key = decoderObject.decodeKey();
            if (key == null) break;
            log.info("key: {}", key);
            boolean finished = false;
            switch (key){
                case "rc":
                    response.setRc(decoderObject.decodeInt());
                    break;
                case "msg":
                    response.setMsg(decoderObject.decodeString());
                    break;
                case "data":
                    if (type.getTypeParameters().length > 0){
                        Object data = parseData(type.getTypeParameters()[0],new Gson().toJson(decoderObject.decodeArbitrary()));
                        response.setData(data);
                    }else{
                        response.setData(decoderObject.decodeArbitrary());
                    }
                    finished = true;
                    break;
            }
            if (finished) break;
        }
        return response;
    }

    public Object parseData(Argument argument,String data){
        try {
            return jsonMapper.readValue(data, argument);
        } catch (IOException e) {
            log.error("parse data error: {}:{}", argument,e.getMessage());
            return null;
        }
    }
}

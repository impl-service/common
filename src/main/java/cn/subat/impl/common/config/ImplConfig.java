package cn.subat.impl.common.config;

import java.util.ArrayList;
import java.util.List;

public interface ImplConfig {
    String getAppKey();
    default List<String> getTopics(){
        return new ArrayList<>();
    }
}

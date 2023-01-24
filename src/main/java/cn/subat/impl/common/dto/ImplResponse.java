package cn.subat.impl.common.dto;


import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.data.model.Page;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.config.naming.SnakeCaseStrategy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Serdeable(naming = SnakeCaseStrategy.class)
public class ImplResponse<T>{
    int rc;
    String msg;
    T data;
    public ImplResponse(T data) {
        this.data = data;
        this.rc = 1;
        this.msg = "ok";
    }
    public ImplResponse(int rc, String msg) {
        this.rc = rc;
        this.msg = msg;
    }

    public static <T> ImplResponse<T> of(int rc, String msg){
        return new ImplResponse<>(rc,msg);
    }
    public static <T> ImplResponse<T> of(T data){
        return new ImplResponse<>(data);
    }
    public static <T> ImplResponse<List<T>> of(List<T> data){
        return new ImplResponse<>(data);
    }
    public static <T> ImplResponse<ImplPage<T>> of(Page<T> data){
        ImplPage<T> page = new ImplPage<>();
        page.setData(data.getContent());
        page.setTotal(data.getTotalSize());
        page.setLastPage(data.getTotalPages());
        page.setCurrentPage(data.getPageNumber()+1);
        page.setPerPage(data.getSize());
        return new ImplResponse<>(page);
    }
}

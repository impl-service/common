package cn.subat.impl.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.config.naming.SnakeCaseStrategy;
import lombok.Data;

import java.util.List;

@Data
@Serdeable(naming = SnakeCaseStrategy.class)
public class ImplPage<T> {
    private List<T> data;

    private Integer currentPage;
    private Long total;
    private Integer lastPage;
    private Integer perPage;
}

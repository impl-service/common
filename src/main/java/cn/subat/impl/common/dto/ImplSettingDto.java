package cn.subat.impl.common.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.data.annotation.NamingStrategy;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.config.naming.SnakeCaseStrategy;
import jakarta.annotation.Nullable;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
@Data
@Serdeable(naming = SnakeCaseStrategy.class)
public class ImplSettingDto {

    private Long id;
    @Size(max = 32)
    @NotNull
    private String appKey;
    @Size(max = 32)
    @NotNull
    private String item;
    private Integer type;
    @Nullable
    private String value;
    @Size(max = 190)
    @NotNull
    private String comment;
}
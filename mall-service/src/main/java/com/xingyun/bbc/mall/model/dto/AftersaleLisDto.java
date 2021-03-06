package com.xingyun.bbc.mall.model.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

@Data
@ApiModel(value = "售后列表dto")
public class AftersaleLisDto extends PageDto implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "供应商id")
    private Long fuserId;
}

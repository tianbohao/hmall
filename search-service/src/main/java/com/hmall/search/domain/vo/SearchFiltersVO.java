package com.hmall.search.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "搜索过滤条件")
public class SearchFiltersVO {

    @ApiModelProperty("分类列表")
    private List<String> category;

    @ApiModelProperty("品牌列表")
    private List<String> brand;
}

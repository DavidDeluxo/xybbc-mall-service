package com.xingyun.bbc.mall.service;

import com.xingyun.bbc.mall.model.dto.SubjectQueryDto;
import com.xingyun.bbc.mall.model.vo.SearchItemListVo;
import com.xingyun.bbc.mall.model.vo.SearchItemVo;
import com.xingyun.bbc.mall.model.vo.SubjectVo;

public interface SubjectService {

    /**
     * 根据主键查询专题信息
     *
     * @param subjectQueryDto
     * @return
     */
    SubjectVo getById(SubjectQueryDto subjectQueryDto);

    /**
     * 查询专题商品
     *
     * @param subjectQueryDto
     * @return
     */
    SearchItemListVo<SearchItemVo> getSubjectGoods(SubjectQueryDto subjectQueryDto);
}

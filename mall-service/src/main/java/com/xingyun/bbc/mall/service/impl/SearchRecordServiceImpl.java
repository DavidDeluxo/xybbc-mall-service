package com.xingyun.bbc.mall.service.impl;

import com.xingyun.bbc.core.enums.ResultStatus;
import com.xingyun.bbc.core.exception.BizException;
import com.xingyun.bbc.core.sku.api.GoodsSearchHistoryApi;
import com.xingyun.bbc.core.sku.po.GoodsSearchHistory;
import com.xingyun.bbc.core.utils.Result;
import com.xingyun.bbc.mall.service.SearchRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SearchRecordServiceImpl implements SearchRecordService {

    @Autowired
    GoodsSearchHistoryApi goodsSearchHistoryApi;

    @Async("threadPoolTaskExecutor")
    @Override
    public Result<Integer> insertSearchRecordAsync(String keyword, Integer fuid) {
        log.info("插入搜索历史:{}", keyword);
        GoodsSearchHistory insertParam = new GoodsSearchHistory();
        if (fuid != null) {
            insertParam.setFuid(Long.parseLong(String.valueOf(fuid)));
        }
        insertParam.setFsearchKeyword(keyword);
        Result<Integer> insertResult = goodsSearchHistoryApi.create(insertParam);
        if (!insertResult.isSuccess()) {
            throw new BizException(ResultStatus.INTERNAL_SERVER_ERROR);
        }
        return insertResult;
    }

}

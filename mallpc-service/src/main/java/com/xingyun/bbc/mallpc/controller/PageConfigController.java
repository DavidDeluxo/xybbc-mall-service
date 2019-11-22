package com.xingyun.bbc.mallpc.controller;

import com.xingyun.bbc.core.utils.Result;
import com.xingyun.bbc.mallpc.model.vo.pageconfig.PageConfigPcVo;
import com.xingyun.bbc.mallpc.service.PageConfigPcService;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("pageconfig")
public class PageConfigController {
    @Resource
    private PageConfigPcService pageConfigPcService;
    /**
     * 导航静态文件缓存 前端获取数据接口
     *
     * @return
     */
    @GetMapping("/navigation")
    public Result<List<PageConfigPcVo>> navigation() {
        return Result.success(pageConfigPcService.navigation());
    }
}

package com.xingyun.bbc.mall.controller;


import com.xingyun.bbc.core.utils.Result;
import com.xingyun.bbc.mall.model.dto.GoodsDetailDto;
import com.xingyun.bbc.mall.model.vo.*;
import com.xingyun.bbc.mall.service.GoodDetailService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@Api("商品详情")
@RestController
@RequestMapping("/goodsDetail")
public class GoodsDetailController {

    public static final Logger logger = LoggerFactory.getLogger(GoodsDetailController.class);

    @Autowired
    private GoodDetailService goodDetailService;

    @ApiOperation(value = "获取商品主图", httpMethod = "GET")
    @GetMapping("/via/getGoodDetailPic")
    public Result<List<String>> getGoodDetailPic(@RequestParam Long fgoodsId, @RequestParam (required = false) Long fskuId){
        return goodDetailService.getGoodDetailPic(fgoodsId, fskuId);
    }

    @ApiOperation(value = "获取商品基本信息", httpMethod = "GET")
    @GetMapping("/via/getGoodDetailBasic")
    public Result<GoodsVo> getGoodDetailBasic(@RequestParam Long fgoodsId, @RequestParam (required = false) Long fskuId){
        return goodDetailService.getGoodDetailBasic(fgoodsId, fskuId);
    }

    @ApiOperation(value = "获取商品属性", httpMethod = "GET")
    @GetMapping("/via/getGoodsAttribute")
    public Result<Map<String, List<GoodsAttributeVo>>> getGoodsAttribute(@RequestParam Long fgoodsId){
        return goodDetailService.getGoodsAttribute(fgoodsId);
    }

    @ApiOperation(value = "获取商品各种规格", httpMethod = "GET")
    @GetMapping("/via/getGoodsSpecifi")
    public Result<GoodspecificationVo> getGoodsSpecifi(@RequestParam Long fgoodsId){
        return goodDetailService.getGoodsSpecifi(fgoodsId);
    }

    @ApiOperation(value = "获取价格", httpMethod = "POST")
    @PostMapping("/getGoodPrice")
    public Result<GoodsPriceVo> getGoodPrice(@RequestBody  GoodsDetailDto goodsDetailDto, HttpServletRequest request){
        Long xyid = Long.parseLong(request.getHeader("xyid"));
        goodsDetailDto.setFuid(xyid);
//      goodsDetailDto.setFuid(1l);
        return goodDetailService.getGoodPrice(goodsDetailDto);
    }

    @ApiOperation(value = "获取库存和销量", httpMethod = "POST")
    @PostMapping("/getGoodStockSell")
    public Result<GoodStockSellVo> getGoodStockSell(@RequestBody GoodsDetailDto goodsDetailDto, HttpServletRequest request){
        Long xyid = Long.parseLong(request.getHeader("xyid"));
        goodsDetailDto.setFuid(xyid);
//      goodsDetailDto.setFuid(1l);
        return goodDetailService.getGoodStockSell(goodsDetailDto);
    }

//    @ApiOperation(value = "获取sku批次有效期", httpMethod = "GET")
//    @GetMapping("/getSkuBatchSpecifi")
//    public Result<List<GoodsSkuBatchVo>> getSkuBatchSpecifi(@RequestParam Long fskuId){
//        return goodDetailService.getSkuBatchSpecifi(fskuId);
//    }
//
//    @ApiOperation(value = "获取sku批次包装规格", httpMethod = "GET")
//    @GetMapping("/getSkuBatchPackageSpecifi")
//    public Result<List<GoodsSkuBatchPackageVo>> getSkuBatchPackageSpecifi(@RequestParam Long fskuBatchId){
//        return goodDetailService.getSkuBatchPackageSpecifi(fskuBatchId);
//    }

}

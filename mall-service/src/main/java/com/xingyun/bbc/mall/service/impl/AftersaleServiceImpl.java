package com.xingyun.bbc.mall.service.impl;

import com.alibaba.fastjson.JSON;
import com.xingyun.bbc.core.enums.ResultStatus;
import com.xingyun.bbc.core.exception.BizException;
import com.xingyun.bbc.core.operate.api.OrderConfigApi;
import com.xingyun.bbc.core.operate.api.ShippingCompanyApi;
import com.xingyun.bbc.core.operate.po.OrderConfig;
import com.xingyun.bbc.core.operate.po.ShippingCompany;
import com.xingyun.bbc.core.order.api.OrderAftersaleAdjustApi;
import com.xingyun.bbc.core.order.api.OrderAftersaleApi;
import com.xingyun.bbc.core.order.api.OrderAftersaleBackApi;
import com.xingyun.bbc.core.order.api.OrderAftersalePicApi;
import com.xingyun.bbc.core.order.enums.OrderAftersaleStatus;
import com.xingyun.bbc.core.order.enums.OrderAftersaleType;
import com.xingyun.bbc.core.order.po.OrderAftersale;
import com.xingyun.bbc.core.order.po.OrderAftersaleAdjust;
import com.xingyun.bbc.core.order.po.OrderAftersaleBack;
import com.xingyun.bbc.core.order.po.OrderAftersalePic;
import com.xingyun.bbc.core.query.Criteria;
import com.xingyun.bbc.core.sku.api.GoodsSkuApi;
import com.xingyun.bbc.core.sku.po.GoodsSku;
import com.xingyun.bbc.core.supplier.api.SupplierTransportSkuApi;
import com.xingyun.bbc.core.supplier.po.SupplierTransportSku;
import com.xingyun.bbc.core.utils.Result;
import com.xingyun.bbc.mall.base.utils.DozerHolder;
import com.xingyun.bbc.mall.base.utils.PageUtils;
import com.xingyun.bbc.mall.base.utils.ResultUtils;
import com.xingyun.bbc.mall.common.constans.MallConstants;
import com.xingyun.bbc.mall.common.exception.MallExceptionCode;
import com.xingyun.bbc.mall.model.dto.AftersaleBackDto;
import com.xingyun.bbc.mall.model.dto.AftersaleLisDto;
import com.xingyun.bbc.mall.model.dto.ShippingCompanyDto;
import com.xingyun.bbc.mall.model.vo.AftersaleBackVo;
import com.xingyun.bbc.mall.model.vo.AftersaleDetailVo;
import com.xingyun.bbc.mall.model.vo.AftersaleListVo;
import com.xingyun.bbc.mall.model.vo.PageVo;
import com.xingyun.bbc.mall.service.AftersaleService;
import com.xingyun.bbc.message.business.MessagePushChannel;
import com.xingyun.bbc.message.model.dto.MsgPushDto;
import com.xingyun.bbc.message.model.dto.MsgTemplateVariableDto;
import com.xingyun.bbc.message.model.enums.PushTypeEnum;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.dozer.Mapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;


@Slf4j
@Service
@EnableBinding(MessagePushChannel.class)
public class AftersaleServiceImpl implements AftersaleService {

    public static final Logger logger = LoggerFactory.getLogger(AftersaleService.class);

    @Autowired
    private OrderAftersaleApi orderAftersaleApi;

    @Autowired
    private OrderAftersaleAdjustApi orderAftersaleAdjustApi;

    @Autowired
    private OrderAftersaleBackApi orderAftersaleBackApi;

    @Autowired
    private OrderAftersalePicApi orderAftersalePicApi;

    @Autowired
    private GoodsSkuApi goodsSkuApi;

    @Autowired
    private OrderConfigApi orderConfigApi;

    @Autowired
    private ShippingCompanyApi shippingCompanyApi;

    @Autowired
    private SupplierTransportSkuApi supplierTransportSkuApi;

    @Autowired
    private PageUtils pageUtils;

    @Autowired
    private Mapper mapper;

    @Autowired
    private DozerHolder dozerHolder;


    @Resource
    private MessagePushChannel messagePushChannel;


    @Override
    public Result<PageVo<AftersaleListVo>> getAftersaleLis(AftersaleLisDto aftersaleLisDto) {
        //????????????????????????
        Criteria<OrderAftersale, Object> criteria = Criteria.of(OrderAftersale.class)
                .andEqualTo(OrderAftersale::getFuid, aftersaleLisDto.getFuserId());
        Result<Integer> countResult = orderAftersaleApi.countByCriteria(criteria);
        if (!countResult.isSuccess()) {
            logger.info("??????user_id {}??????????????????????????????", aftersaleLisDto.getFuserId());
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        if (countResult.getData().intValue() == 0) {
            return Result.success();
        }

        //????????????1??????????????? 2??????????????? 3??????????????? 4??????????????? 5????????? 6????????? 7????????? 8????????? 9?????????  ???????????????????????????
        criteria.fields(OrderAftersale::getForderAftersaleId, OrderAftersale::getFskuCode,
                OrderAftersale::getFaftersaleNum, OrderAftersale::getFaftersaleStatus,
                OrderAftersale::getFunitPrice, OrderAftersale::getFbatchPackageNum,
                OrderAftersale::getFtransportOrderId)
                .page(aftersaleLisDto.getCurrentPage(), aftersaleLisDto.getPageSize())
                .sortDesc(OrderAftersale::getFcreateTime);

        Result<List<OrderAftersale>> listResult = orderAftersaleApi.queryByCriteria(criteria);
        if (!listResult.isSuccess()) {
            logger.info("??????user_id {}??????????????????????????????", aftersaleLisDto.getFuserId());
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }

        PageVo<AftersaleListVo> result = pageUtils.convert(countResult.getData(), listResult.getData(), AftersaleListVo.class, aftersaleLisDto);
        List<AftersaleListVo> aftersaleList = result.getList();
        if (CollectionUtils.isEmpty(aftersaleList)) {
            return Result.success();
        }

        List<String> skuCodeList = aftersaleList.stream().map(AftersaleListVo::getFskuCode).distinct().collect(Collectors.toList());

        Result<List<GoodsSku>> goodsSkuResult = goodsSkuApi.queryByCriteria(Criteria.of(GoodsSku.class)
                .andIn(GoodsSku::getFskuCode, skuCodeList)
                .fields(GoodsSku::getFskuCode, GoodsSku::getFskuName, GoodsSku::getFskuThumbImage));
        if (!goodsSkuResult.isSuccess()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        List<GoodsSku> goodsSkuList = ResultUtils.getListNotEmpty(goodsSkuResult, MallExceptionCode.SKU_IS_NONE);
        Map<String, List<GoodsSku>> goodsSkuMap = goodsSkuList.stream().collect(Collectors.groupingBy(GoodsSku::getFskuCode));

        //??????skuName
        for (AftersaleListVo aftersaleListVo : aftersaleList) {
            GoodsSku skuInfor = goodsSkuMap.get(aftersaleListVo.getFskuCode()).get(0);
            if (Objects.nonNull(skuInfor)) {
                aftersaleListVo.setFskuName(skuInfor.getFskuName());
                aftersaleListVo.setFskuPic(skuInfor.getFskuThumbImage());
            }
            aftersaleListVo.setFbatchPackageName(aftersaleListVo.getFbatchPackageNum() + "??????");
            aftersaleListVo.setFunitPrice(aftersaleListVo.getFunitPrice().divide(MallConstants.ONE_HUNDRED, 2, BigDecimal.ROUND_HALF_UP));
            aftersaleListVo.setFaftersaleNumShow(this.getAftersaleNumShow(aftersaleListVo.getFaftersaleNum(), aftersaleListVo.getFtransportOrderId(), aftersaleListVo.getFskuCode()));
        }
        return Result.success(result);
    }

    private String getAftersaleNumShow(Integer faftersaleNum, String ftransportOrderId, String fskuCode) {
        //????????????????????? faftersaleNum ?????????(????????????) ??????faftersaleNum/????????????
        if (StringUtils.isEmpty(ftransportOrderId)) {
            return faftersaleNum.toString();
        } else {
            Result<SupplierTransportSku> supplierTransportSkuResult = supplierTransportSkuApi.queryOneByCriteria(Criteria.of(SupplierTransportSku.class)
                    .andEqualTo(SupplierTransportSku::getFskuCode, fskuCode)
                    .andEqualTo(SupplierTransportSku::getFtransportOrderId, ftransportOrderId)
                    .fields(SupplierTransportSku::getFbatchPackageNum, SupplierTransportSku::getFskuNum));
            if (!supplierTransportSkuResult.isSuccess()) {
                throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
            }
            if (null != supplierTransportSkuResult.getData()) {
                SupplierTransportSku data = supplierTransportSkuResult.getData();
                Long fbatchPackageNum = data.getFbatchPackageNum();
                Integer fskuNum = data.getFskuNum();
                Long total = fbatchPackageNum * fskuNum;
                return new StringBuffer(faftersaleNum.toString()).append("/").append(total).toString();
            }
            return "";
        }
    }

    @Override
    public Result<AftersaleDetailVo> getAftersaleDetail(String faftersaleId) {
        //??????????????????????????????
        Result<OrderAftersale> aftersaleBasicResult = orderAftersaleApi.queryOneByCriteria(Criteria.of(OrderAftersale.class)
                .andEqualTo(OrderAftersale::getForderAftersaleId, faftersaleId)
                .fields(OrderAftersale::getForderAftersaleId, OrderAftersale::getFskuCode, OrderAftersale::getFaftersaleNum,
                        OrderAftersale::getFaftersaleStatus, OrderAftersale::getFbatchPackageNum, OrderAftersale::getFunitPrice,
                        OrderAftersale::getFaftersaleReason, OrderAftersale::getFaftersaleType, OrderAftersale::getFtransportOrderId,
                        OrderAftersale::getFcreateTime, OrderAftersale::getFmodifyTime));
        if (!aftersaleBasicResult.isSuccess()) {
            logger.info("??????faftersaleId {}??????????????????????????????", faftersaleId);
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }

        //??????skuName
        AftersaleDetailVo aftersaleDetailVo = mapper.map(aftersaleBasicResult.getData(), AftersaleDetailVo.class);
        GoodsSku skuInfor = this.getSkuInfor(aftersaleDetailVo.getFskuCode());
        aftersaleDetailVo.setFskuName(skuInfor.getFskuName());
        aftersaleDetailVo.setFskuPic(skuInfor.getFskuThumbImage());
        aftersaleDetailVo.setFbatchPackageName(aftersaleDetailVo.getFbatchPackageNum() + "??????");
        aftersaleDetailVo.setFunitPrice(aftersaleDetailVo.getFunitPrice().divide(MallConstants.ONE_HUNDRED, 2, BigDecimal.ROUND_HALF_UP));
        aftersaleDetailVo.setFaftersaleNumShow(this.getAftersaleNumShow(aftersaleDetailVo.getFaftersaleNum(), aftersaleDetailVo.getFtransportOrderId(), aftersaleDetailVo.getFskuCode()));

        //?????????????????????
        Result<OrderAftersaleAdjust> aftersaleAdjustResult = orderAftersaleAdjustApi.queryOneByCriteria(Criteria.of(OrderAftersaleAdjust.class)
                .andEqualTo(OrderAftersaleAdjust::getForderAftersaleId, faftersaleId)
                .fields(OrderAftersaleAdjust::getFaftersaleTotalAmount));
        if (!aftersaleAdjustResult.isSuccess()) {
            logger.info("??????faftersaleId {}?????????????????????????????????", faftersaleId);
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        Long faftersaleTotalAmount = aftersaleAdjustResult.getData().getFaftersaleTotalAmount();
        aftersaleDetailVo.setFaftersaleTotalAmount(new BigDecimal(faftersaleTotalAmount).divide(MallConstants.ONE_HUNDRED, 2, BigDecimal.ROUND_HALF_UP));

        //????????????faftersale_status 1????????????2????????????3????????????4????????????6????????????7????????????8????????? 9????????????10???????????????11???????????????12??????????????????13???????????????14?????????????????????15????????????
        //????????????faftersale_type 1 ?????? 2 ???????????? ??????????????????????????????
        if (OrderAftersaleType.RETURN_MONEY_AND_GOODS.getCode().equals(aftersaleDetailVo.getFaftersaleType())) {

            //??????????????? 6 ???????????????????????????????????????????????????
            if (OrderAftersaleStatus.WAIT_RETURN_GOODS.getCode().equals(aftersaleDetailVo.getFaftersaleStatus())) {
                Result<OrderAftersaleBack> aftersaleBackResult = orderAftersaleBackApi.queryOneByCriteria(Criteria.of(OrderAftersaleBack.class)
                        .andEqualTo(OrderAftersaleBack::getForderAftersaleId, faftersaleId)
                        .fields(OrderAftersaleBack::getFdeliveryName, OrderAftersaleBack::getFdeliveryMobile, OrderAftersaleBack::getFdeliveryProvince,
                                OrderAftersaleBack::getFdeliveryCity, OrderAftersaleBack::getFdeliveryArea, OrderAftersaleBack::getFdeliveryAddr));
                if (!aftersaleBackResult.isSuccess()) {
                    logger.info("??????faftersaleId {}??????????????????????????????", faftersaleId);
                    throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
                }
                OrderAftersaleBack aftersaleBack = aftersaleBackResult.getData();
                if (null != aftersaleBack) {
                    aftersaleDetailVo.setFdeliveryName(aftersaleBack.getFdeliveryName());
                    aftersaleDetailVo.setFdeliveryMobile(aftersaleBack.getFdeliveryMobile());
                    aftersaleDetailVo.setFdeliveryProvince(aftersaleBack.getFdeliveryProvince());
                    aftersaleDetailVo.setFdeliveryCity(aftersaleBack.getFdeliveryCity());
                    aftersaleDetailVo.setFdeliveryArea(aftersaleBack.getFdeliveryArea());
                    aftersaleDetailVo.setFdeliveryAddr(aftersaleBack.getFdeliveryAddr());
                }

                //???????????????????????????
                Result<OrderConfig> orderConfigResult = orderConfigApi.queryOneByCriteria(Criteria.of(OrderConfig.class)
                        .andEqualTo(OrderConfig::getForderConfigType, 3)
                        .fields(OrderConfig::getFminute));
                if (!orderConfigResult.isSuccess()) {
                    logger.info("???????????????????????????????????????????????????");
                    throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
                }
                Long fminute = orderConfigResult.getData().getFminute();
                Long time = aftersaleBasicResult.getData().getFmodifyTime().getTime();
                Date reGoodsTime = new Date(fminute * 60 * 1000 + time);
                aftersaleDetailVo.setFreGoodsTime(reGoodsTime);
            }
        }
        if (aftersaleDetailVo.getFaftersaleStatus() <= OrderAftersaleStatus.WAIT_FINANCE_VERIFY.getCode()) {
            aftersaleDetailVo.setFrefundTime(aftersaleBasicResult.getData().getFcreateTime());
        } else {
            aftersaleDetailVo.setFrefundTime(aftersaleBasicResult.getData().getFmodifyTime());
        }
        return Result.success(aftersaleDetailVo);
    }

    @Override
    public Result<List<ShippingCompanyDto>> getShippingCompanyLis(ShippingCompanyDto shippingCompanyDto) {
        Criteria<ShippingCompany, Object> criteria = Criteria.of(ShippingCompany.class);
        if (!StringUtils.isEmpty(shippingCompanyDto.getFshippingName())) {
            criteria.andLike(ShippingCompany::getFshippingName, shippingCompanyDto.getFshippingName() + "%");
        }
        Result<List<ShippingCompany>> listResult = shippingCompanyApi.queryByCriteria(criteria);
        List<ShippingCompanyDto> result = dozerHolder.convert(listResult.getData(), ShippingCompanyDto.class);
        return Result.success(result);
    }

    @Override
    @GlobalTransactional
    public Result modifyAftersaleBack(AftersaleBackDto aftersaleBackDto) {
        //??????????????????
        Result<OrderAftersale> statusResult = orderAftersaleApi.queryOneByCriteria(Criteria.of(OrderAftersale.class)
                .andEqualTo(OrderAftersale::getForderAftersaleId, aftersaleBackDto.getForderAftersaleId())
                .fields(OrderAftersale::getFaftersaleStatus));
        if (!statusResult.isSuccess()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        if (!OrderAftersaleStatus.WAIT_RETURN_GOODS.getCode().equals(statusResult.getData().getFaftersaleStatus())) {
            return Result.success();
        }
        //??????orderAftersaleBack id
        Result<OrderAftersaleBack> aftersaleBackResult = orderAftersaleBackApi.queryOneByCriteria(Criteria.of(OrderAftersaleBack.class)
                .andEqualTo(OrderAftersaleBack::getForderAftersaleId, aftersaleBackDto.getForderAftersaleId())
                .fields(OrderAftersaleBack::getFaftersaleBackId));
        if (!aftersaleBackResult.isSuccess() || null == aftersaleBackResult.getData()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        OrderAftersaleBack aftersaleBack = mapper.map(aftersaleBackDto, OrderAftersaleBack.class);
        aftersaleBack.setFaftersaleBackId(aftersaleBackResult.getData().getFaftersaleBackId());
        //?????????????????????
        Result<Integer> insResult = orderAftersaleBackApi.updateNotNull(aftersaleBack);
        if (!insResult.isSuccess()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        if (null != aftersaleBackDto.getFpicStr()) {
            String[] picStr = aftersaleBackDto.getFpicStr().split(",");
            List<OrderAftersalePic> aftersalePicLis = new ArrayList<>();
            Date nowDate = new Date();
            for (String pic : picStr) {
                OrderAftersalePic aftersalePic = new OrderAftersalePic();
                aftersalePic.setForderAftersaleId(aftersaleBackDto.getForderAftersaleId());
                aftersalePic.setFpicType(2);
                aftersalePic.setFaftersalePic(pic);
                aftersalePic.setFcreateTime(nowDate);
                aftersalePic.setFmodifyTime(nowDate);
                aftersalePicLis.add(aftersalePic);

            }
            Result<Integer> picInsResult = orderAftersalePicApi.createBatch(aftersalePicLis);
            if (!picInsResult.isSuccess()) {
                throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
            }
        }
        //??????????????????--???????????????????????????--??????????????????
        Result<OrderAftersale> queryAfterSaleResult = orderAftersaleApi.queryOneByCriteria(Criteria.of(OrderAftersale.class)
                .andEqualTo(OrderAftersale::getForderAftersaleId, aftersaleBackDto.getForderAftersaleId())
                .fields(OrderAftersale::getFmodifyTime,OrderAftersale::getFsupplierId,OrderAftersale::getFsupplierOrderId));
        if (!queryAfterSaleResult.isSuccess()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        OrderAftersale upAftersale = queryAfterSaleResult.getData();
        upAftersale.setForderAftersaleId(aftersaleBackDto.getForderAftersaleId());
        upAftersale.setFaftersaleStatus(OrderAftersaleStatus.WAIT_RETURN_MONEY.getCode());
        Result<Integer> aftersaleResult = orderAftersaleApi.updateNotNull(upAftersale);
        if (!aftersaleResult.isSuccess()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        //??????????????????????????? ???????????????
        try {
            sendMessage(upAftersale);
        }catch (Exception e){
            log.info("???????????????????????????,?????????????????????{}",e.getMessage(),e);
        }

        return Result.success();
    }

    @Override
    public Result<AftersaleBackVo> getAftersaleBackShipping(String faftersaleId) {
        Result<OrderAftersaleBack> aftersaleBackResult = orderAftersaleBackApi.queryOneByCriteria(Criteria.of(OrderAftersaleBack.class)
                .andEqualTo(OrderAftersaleBack::getForderAftersaleId, faftersaleId)
                .fields(OrderAftersaleBack::getForderAftersaleId, OrderAftersaleBack::getFlogisticsCompanyId, OrderAftersaleBack::getFbackLogisticsOrder,
                        OrderAftersaleBack::getFbackRemark, OrderAftersaleBack::getFbackMobile));
        if (!aftersaleBackResult.isSuccess()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        AftersaleBackVo aftersaleBackVo = new AftersaleBackVo();
        if (null == aftersaleBackResult.getData()) {
            return Result.success(aftersaleBackVo);
        }
        aftersaleBackVo = mapper.map(aftersaleBackResult.getData(), AftersaleBackVo.class);
        //????????????????????????
        Result<ShippingCompany> shippingCompanyResult = shippingCompanyApi.queryOneByCriteria(Criteria.of(ShippingCompany.class)
                .andEqualTo(ShippingCompany::getFshippingCompanyId, aftersaleBackVo.getFlogisticsCompanyId())
                .fields(ShippingCompany::getFshippingName));
        if (!shippingCompanyResult.isSuccess()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        aftersaleBackVo.setFlogisticsCompanyName(null != shippingCompanyResult.getData() ? shippingCompanyResult.getData().getFshippingName() : "");
        Result<List<OrderAftersalePic>> picResult = orderAftersalePicApi.queryByCriteria(Criteria.of(OrderAftersalePic.class)
                .andEqualTo(OrderAftersalePic::getFpicType, 2)
                .andEqualTo(OrderAftersalePic::getForderAftersaleId, faftersaleId)
                .fields(OrderAftersalePic::getFaftersalePic));
        if (!picResult.isSuccess()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        String picUrl = "";
        if (!CollectionUtils.isEmpty(picResult.getData())) {
            StringBuffer sf = new StringBuffer();
            for (OrderAftersalePic pic : picResult.getData()) {
                sf.append(pic.getFaftersalePic()).append(",");
            }
            picUrl = sf.toString();
        }
        aftersaleBackVo.setFpicStr(picUrl);
        return Result.success(aftersaleBackVo);
    }

    private GoodsSku getSkuInfor(String skuCode) {
        GoodsSku goodsSku = new GoodsSku();
        String skuName = "";
        String skuPic = "";
        Result<GoodsSku> goodsSkuResult = goodsSkuApi.queryOneByCriteria(Criteria.of(GoodsSku.class)
                .andEqualTo(GoodsSku::getFskuCode, skuCode)
                .fields(GoodsSku::getFskuName, GoodsSku::getFskuThumbImage));
        if (!goodsSkuResult.isSuccess()) {
            throw new BizException(ResultStatus.REMOTE_SERVICE_ERROR);
        }
        if (null != goodsSkuResult.getData()) {
            skuName = goodsSkuResult.getData().getFskuName();
            skuPic = goodsSkuResult.getData().getFskuThumbImage();
        }
        goodsSku.setFskuName(skuName);
        goodsSku.setFskuThumbImage(skuPic);
        return goodsSku;
    }

    private void sendMessage(OrderAftersale orderAftersale) {
        MsgPushDto msgPushDto = new MsgPushDto();
        MsgTemplateVariableDto msgTemplateVariableDto = new MsgTemplateVariableDto();
        if (orderAftersale.getFaftersaleStatus().toString().equals(OrderAftersaleStatus.WAIT_RETURN_MONEY.getCode().toString())) {
            msgTemplateVariableDto.setForderAftersaleId(orderAftersale.getForderAftersaleId());
            msgTemplateVariableDto.setFsupplierOrderId(orderAftersale.getFsupplierOrderId());
            msgPushDto.setMsgTemplateVariable(msgTemplateVariableDto);
            msgPushDto.setSystemTemplateType(8);
            msgPushDto.setPushType(PushTypeEnum.SYSTEM_NOTIFY.getKey());
            msgPushDto.setSubjectType(2);
            msgPushDto.setSubjectId(orderAftersale.getFsupplierId());
            Message<MsgPushDto> message = MessageBuilder.withPayload(msgPushDto).build();
            boolean result = messagePushChannel.systemNoticeOut().send(message);
            if (result) {
                log.info("???????????????????????????????????????message={}", JSON.toJSONString(msgPushDto));
            } else {
                log.warn("???????????????????????????????????????message={}", JSON.toJSONString(msgPushDto));
            }
        }
    }
}

package com.xingyun.bbc.mall.service.impl;

import com.google.common.collect.Lists;
import com.xingyun.bbc.core.exception.BizException;
import com.xingyun.bbc.core.operate.api.MessageUserRecordApi;
import com.xingyun.bbc.core.operate.po.MessageUserRecord;
import com.xingyun.bbc.core.order.api.OrderPaymentApi;
import com.xingyun.bbc.core.order.po.OrderPayment;
import com.xingyun.bbc.core.query.Criteria;
import com.xingyun.bbc.core.supplier.api.SupplierOrderSkuApi;
import com.xingyun.bbc.core.supplier.api.SupplierTransportOrderApi;
import com.xingyun.bbc.core.supplier.po.SupplierOrderSku;
import com.xingyun.bbc.core.supplier.po.SupplierTransportOrder;
import com.xingyun.bbc.core.utils.Result;
import com.xingyun.bbc.express.api.ExpressBillProviderApi;
import com.xingyun.bbc.express.model.dto.ExpressBillDto;
import com.xingyun.bbc.express.model.vo.ExpressBillDetailVo;
import com.xingyun.bbc.express.model.vo.ExpressBillVo;
import com.xingyun.bbc.mall.common.enums.MessageAutoTypeEnum;
import com.xingyun.bbc.mall.common.enums.MessageGroupTypeEnum;
import com.xingyun.bbc.mall.common.enums.MessageManualTypeEnum;
import com.xingyun.bbc.mall.common.enums.MessagePushTypeEnum;
import com.xingyun.bbc.mall.common.exception.MallExceptionCode;
import com.xingyun.bbc.mall.model.dto.MessageQueryDto;
import com.xingyun.bbc.mall.model.dto.MessageUpdateDto;
import com.xingyun.bbc.mall.model.vo.MessageCenterVo;
import com.xingyun.bbc.mall.model.vo.MessageListVo;
import com.xingyun.bbc.mall.model.vo.MessageSelfInfoVo;
import com.xingyun.bbc.mall.model.vo.PageVo;
import com.xingyun.bbc.mall.service.MessageService;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @Description 消息中心 - 实现类
 * @ClassName MessageServiceImpl
 * @Author ming.yiFei
 * @Date 2019/12/23 11:37
 **/
@Service
public class MessageServiceImpl implements MessageService {

    @Resource
    private MessageUserRecordApi userRecordApi;

    @Resource
    private SupplierTransportOrderApi transportOrderApi;

    @Resource
    private ExpressBillProviderApi expressBillProvider;

    @Resource
    private OrderPaymentApi paymentApi;

    @Resource
    private SupplierOrderSkuApi supplierOrderSkuApi;

    /**
     * @param userId
     * @return
     * @see MessageService#queryMessageGroupByUserId(Long)
     */
    @Override
    public Result<List<MessageCenterVo>> queryMessageGroupByUserId(Long userId) {

        Result<List<MessageUserRecord>> userRecordResult = userRecordApi.queryByCriteria(Criteria.of(MessageUserRecord.class)
                .fields(MessageUserRecord::getFtype
                        , MessageUserRecord::getFtitle
                        , MessageUserRecord::getFcreateTime)
                .andEqualTo(MessageUserRecord::getFreaded, 0)
                .andEqualTo(MessageUserRecord::getFsendStatus, 2)
                .andEqualTo(MessageUserRecord::getFuid, userId)
                .andGreaterThanOrEqualTo(MessageUserRecord::getFexpirationDate, new Date())
                .sortDesc(MessageUserRecord::getFcreateTime));
        if (!userRecordResult.isSuccess()) {
            throw new BizException(MallExceptionCode.SYSTEM_ERROR);
        }
        List<MessageUserRecord> userRecords = userRecordResult.getData();
        if (CollectionUtils.isEmpty(userRecords)) {
            ArrayList<MessageCenterVo> messageCenterVos = Lists.newArrayList();
            for (int i = 0; i < MessageGroupTypeEnum.values().length; i++) {
                messageCenterVos.add(new MessageCenterVo(MessageGroupTypeEnum.values()[i].getCode()));
            }
            return Result.success(messageCenterVos);
        }
        ArrayList<MessageCenterVo> messageCenterVos = Lists.newArrayList();
        Map<Integer, List<MessageUserRecord>> messageGroup = userRecords.stream().collect(Collectors.groupingBy(MessageUserRecord::getFmessageGroup));
        for (Map.Entry<Integer, List<MessageUserRecord>> recordEntry : messageGroup.entrySet()) {
            Integer recordEntryKey = recordEntry.getKey();
            List<MessageUserRecord> recordList = recordEntry.getValue();
            MessageUserRecord messageUserRecord = recordList.get(0);
            messageCenterVos.add(new MessageCenterVo(recordEntryKey
                    , messageUserRecord.getFtitle()
                    , recordList.size()
                    , messageUserRecord.getFcreateTime().getTime()));
        }
        List<Integer> types = messageCenterVos.stream().map(MessageCenterVo::getMessageGroupType).collect(Collectors.toList());
        for (int i = 0; i < MessageGroupTypeEnum.values().length; i++) {
            Integer code = MessageGroupTypeEnum.values()[i].getCode();
            if (!types.contains(code)) {
                messageCenterVos.add(new MessageCenterVo(code));
            }
        }
        return Result.success(messageCenterVos);
    }

    /**
     * @param dto
     * @return
     * @see MessageService#queryMessageList(MessageQueryDto)
     */
    @Override
    public Result<PageVo<MessageListVo>> queryMessageList(MessageQueryDto dto) {
        Criteria<MessageUserRecord, Object> userRecordObjectCriteria = Criteria.of(MessageUserRecord.class)
                .andEqualTo(MessageUserRecord::getFreaded, 0)
                .andEqualTo(MessageUserRecord::getFsendStatus, 2)
                .andEqualTo(MessageUserRecord::getFuid, dto.getUserId())
                .andEqualTo(MessageUserRecord::getFtype, dto.getMessageCenterType())
                .andGreaterThanOrEqualTo(MessageUserRecord::getFexpirationDate, new Date())
                .sortDesc(MessageUserRecord::getFcreateTime);
        Result<Integer> userRecordResult = userRecordApi.countByCriteria(userRecordObjectCriteria);
        if (!userRecordResult.isSuccess()) {
            throw new BizException(MallExceptionCode.SYSTEM_ERROR);
        }
        Integer count = userRecordResult.getData();
        if (count < 1) {
            return Result.success(new PageVo(0, dto.getCurrentPage(), dto.getPageSize(), Lists.newArrayList()));
        }
        Result<List<MessageUserRecord>> userRecordsResult = userRecordApi.queryByCriteria(userRecordObjectCriteria);
        if (!userRecordsResult.isSuccess()) {
            throw new BizException(MallExceptionCode.SYSTEM_ERROR);
        }
        List<MessageUserRecord> userRecords = userRecordsResult.getData();
        ArrayList<MessageListVo> messageListVos = Lists.newArrayList();
        userRecords.stream().forEach(record -> {
            MessageListVo messageListVo = new MessageListVo();
            messageListVo.setMessageId(record.getFmessageUserRecordId());
            messageListVo.setTitle(record.getFtitle());
            messageListVo.setReceiveTime(record.getFcreateTime());
            messageListVo.setIsRead(record.getFreaded());
            messageListVo.setPushType(record.getFpushType());
            messageListVo.setRedirectType(record.getFredirectType());
            // 消息类型
            Integer ftype = record.getFtype();
            messageListVo.setMessageType(ftype);
            // 手动、自动
            MessagePushTypeEnum pushTypeEnum = MessagePushTypeEnum.getEnum(record.getFpushType());
            switch (pushTypeEnum){
                // 自动消息类型
                case AUTO:
                    MessageAutoTypeEnum autoTypeEnum = MessageAutoTypeEnum.getEnum(ftype);
                    switch (autoTypeEnum){
                        // 发货单发货
                        case GOODS_SHIPPED:
                            // 发货单号、商品数量、订单号、收件人
                            String frefId = record.getFrefId();
                            Result<SupplierTransportOrder> transportOrderResult = transportOrderApi.queryById(frefId);
                            if (!transportOrderResult.isSuccess() || transportOrderResult.getData() == null) {
                                throw new BizException(MallExceptionCode.SYSTEM_ERROR);
                            }
                            SupplierTransportOrder transportOrder = transportOrderResult.getData();
                            String fshippingCode = transportOrder.getFshippingCode();
                            String fshippingName = transportOrder.getFshippingName();
                            ExpressBillDto expressBillDto = new ExpressBillDto();
                            expressBillDto.setTransportOrderId(frefId);
                            expressBillDto.setCompanyCode(fshippingCode);
                            expressBillDto.setCompanyName(fshippingName);
                            Result<ExpressBillVo> billVoResult = expressBillProvider.query(expressBillDto);
                            if(!billVoResult.isSuccess()){
                                throw new BizException(MallExceptionCode.SYSTEM_ERROR);
                            }
                            ExpressBillVo expressBillVo = billVoResult.getData();
                            List<ExpressBillDetailVo> expressBillVoData = expressBillVo.getData();
                            if (CollectionUtils.isEmpty(expressBillVoData)) {
                                throw new BizException(new MallExceptionCode("", "未查到物流信息"));
                            }
                            ExpressBillDetailVo billDetailVo = expressBillVoData.get(0);
                            // 发货单号、订单号
                            MessageSelfInfoVo selfInfoVo = new MessageSelfInfoVo();
                            selfInfoVo.setOrderLogisticsNo(frefId);
                            selfInfoVo.setTrajectoryContext(billDetailVo.getContext());
                            selfInfoVo.setTrajectoryTime(billDetailVo.getFtime());
                            selfInfoVo.setOrderId(transportOrder.getForderId());
                            // 商品数量
                            Result<List<SupplierOrderSku>> countByCriteria = supplierOrderSkuApi.queryByCriteria(Criteria.of(SupplierOrderSku.class)
                                    .andEqualTo(SupplierOrderSku::getFsupplierOrderId, transportOrder.getFsupplierOrderId()));
                            if(!countByCriteria.isSuccess()){
                                throw new BizException(MallExceptionCode.SYSTEM_ERROR);
                            }
                            List<SupplierOrderSku> orderSkus = countByCriteria.getData();
                            selfInfoVo.setSkuNum(orderSkus.size());
                            // 收件人
                            Result<OrderPayment> orderPaymentResult = paymentApi.queryById(transportOrder.getForderPaymentId());
                            if(!orderPaymentResult.isSuccess()){
                                throw new BizException(MallExceptionCode.SYSTEM_ERROR);
                            }
                            selfInfoVo.setDeliveryName(orderPaymentResult.getData().getFdeliveryName());
                            messageListVo.setSelfInfoVo(selfInfoVo);
                            break;
                        // 注册成功
                        case REGISTER_SUCCESSED:
                            break;
                        // 修改手机号
                        case MODIFY_NUMBER:
                            break;
                        // 优惠券到账
                        case COUPON_RECEIVE:
                            break;
                        // 优惠券即将到期(24小时)
                        case COUPON_ALMOST_OVERDUE:
                            break;
                        // 用户认证成功
                        case USER_VERIFY:
                            break;
                        default:
                            throw new BizException(new MallExceptionCode("", "不存在的消息类型"));
                    }

                    break;
                // 手动消息类型
                case MANUAL:
                    MessageManualTypeEnum manualTypeEnum = MessageManualTypeEnum.getEnum(ftype);
                    switch (manualTypeEnum){
                        case XY_ANNOUNCEMENT:


                            break;
                        case GOODS_MESSAGE:
                            break;
                        case OTHER:
                            break;
                        default:
                            throw new BizException(new MallExceptionCode("", "不存在的消息类型"));
                    }
                    break;
                default:
                    break;
            }
            messageListVos.add(messageListVo);
        });


        return null;
    }

    @Override
    public Result<MessageCenterVo> queryMessageDetailById(MessageQueryDto dto) {
        return null;
    }

    @Override
    public Result updateMessageForRead(MessageUpdateDto dto) {
        return null;
    }
}
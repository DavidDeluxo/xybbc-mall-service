package com.xingyun.bbc.mall.infrastructure.message.interceptor;

import com.alibaba.fastjson.JSONObject;
import com.xingyun.bbc.core.operate.enums.TemplateTypeEnum;
import com.xingyun.bbc.message.business.MessagePushChannel;
import com.xingyun.bbc.message.business.WaitSendInfo;
import com.xingyun.bbc.message.model.dto.MsgPushDto;
import com.xingyun.bbc.message.model.dto.MsgTemplateVariableDto;
import com.xingyun.bbc.message.model.enums.MsgSubjectType;
import com.xingyun.bbc.message.model.enums.PushTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @author: xuxianbei
 * Date: 2019/12/25
 * Time: 20:10
 * Version:V1.0
 */
@Component
@EnableBinding(MessagePushChannel.class)
@Slf4j
public class ModifyMobileMessage {

    @Resource
    MessagePushChannel registerChannel;


    public void onApplicationEvent(WaitSendInfo waitSendInfo) {
        try {
            MsgPushDto msgPushDto = new MsgPushDto();
            MsgTemplateVariableDto msgTemplateVariableDto = new MsgTemplateVariableDto();
            msgTemplateVariableDto.setFmobile(waitSendInfo.getBusinessId());
            msgPushDto.setMsgTemplateVariable(msgTemplateVariableDto);
            msgPushDto.setSystemTemplateType(TemplateTypeEnum.MODIFY_NUMBER.getKey());
            msgPushDto.setPushType(PushTypeEnum.SYSTEM_NOTIFY.getKey());
            //平台会员
            msgPushDto.setSubjectType(MsgSubjectType.USER.getCode());
            msgPushDto.setSubjectId(waitSendInfo.getTargetId());
            Message<MsgPushDto> message = MessageBuilder.withPayload(msgPushDto).build();
            boolean result = registerChannel.systemNoticeOut().send(message);
            if (result) {
                log.info("修改手机发送消息成功->" + JSONObject.toJSONString(message));
            }
        } catch (Exception e) {
            log.error("消息异常", e);
        }
    }
}

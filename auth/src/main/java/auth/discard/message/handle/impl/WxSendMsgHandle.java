package auth.discard.message.handle.impl;

import lombok.extern.slf4j.Slf4j;
import auth.discard.message.handle.ISendMsgHandle;
@Slf4j
public class WxSendMsgHandle implements ISendMsgHandle {

	@Override
	public void SendMsg(String es_receiver, String es_title, String es_content) {
		// TODO Auto-generated method stub
		log.info("发微信消息模板");
	}

}

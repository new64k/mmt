package lemon.weixin.biz;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lemon.shared.api.MmtAPI;
import lemon.shared.common.MsgParser;
import lemon.weixin.WeiXin;
import lemon.weixin.bean.WeiXinConfig;
import lemon.weixin.bean.log.MsgLog;
import lemon.weixin.bean.log.SiteAccessLog;
import lemon.weixin.biz.parser.WXMsgParser;
import lemon.weixin.dao.WXLogManager;
import lemon.weixin.util.WXHelper;

/**
 * The WeiXin API for message
 * 
 * @author lemon
 * 
 */
@Service
public class WeiXinAPI implements MmtAPI {
	private static Log logger = LogFactory.getLog(WeiXinAPI.class);
	@Autowired
	private WXLogManager wxLogManager;
	private MsgParser parser;

	@Override
	public boolean verifySignature(Map<String, Object> params) {
		SiteAccessLog log = (SiteAccessLog) params.get("SiteAccess");
		if (null == log || log.getSignature() == null)
			return false;
		// save log
		saveAccessLog(log);
		// nonce,token,timestamp dictionary sort
		List<String> list = new ArrayList<>();
		list.add(log.getNonce());
		list.add(log.getToken());
		list.add(log.getTimestamp());
		Collections.sort(list);
		StringBuilder sb = new StringBuilder();
		for (String str : list) {
			sb.append(str);
		}
		// sha1 for signature
		String sh1str = WXHelper.sha1(log.getSignature());
		// compare
		return sb.toString().equalsIgnoreCase(sh1str);
	}

	@Override
	public String processMsg(String token, String msg) {
		WeiXinConfig cfg = WeiXin.getConfig(token);
		int cust_id = cfg.getCust_id();
		//save received log
		saveReciveMessageLog(cust_id, msg);
		//get message type
		String msgType = getMsgType(msg);
		//get message parser
		parser = WXMsgParser.getParser(msgType);
		if(null == parser)
			throw new WeiXinException("No parser find.");
		//process message and generate replay message
		String rMsg = parser.parseMessage(token, msg);
		//save log
		if(rMsg == null)
			saveSendMessageLog(cust_id, rMsg);
		//replay WeiXin message
		return rMsg;
	}

	/**
	 * save access log
	 * @param log
	 */
	private void saveAccessLog(SiteAccessLog log) {
		wxLogManager.saveSiteAccessLog(log);
	}
	
	/**
	 * save revive message log
	 * @param cust_id
	 * @param msg
	 */
	private void saveReciveMessageLog(int cust_id, String msg){
		MsgLog log = MsgLog.createReciveLog(cust_id, msg);
		wxLogManager.saveMessageLog(log);
	}
	
	/**
	 * save send message log
	 * @param cust_id
	 * @param msg
	 */
	private void saveSendMessageLog(int cust_id, String msg){
		MsgLog log = MsgLog.createSendLog(cust_id, msg);
		wxLogManager.saveMessageLog(log);
	}


	/**
	 * Get message type
	 * @param msg
	 * @return
	 */
	private String getMsgType(String msg) {
		InputStream is = null;
		try {
			try {
				is = new ByteArrayInputStream(msg.getBytes());
				Document doc = new SAXBuilder().build(is);
				Element e = doc.getRootElement().getChild("MsgType");
				return e.getValue();
			} finally {
				if (null != is)
					is.close();
			}
		} catch (IOException | JDOMException e) {
			logger.error("Can't get message type:" + e.getMessage());
		}
		return null;
	}

}

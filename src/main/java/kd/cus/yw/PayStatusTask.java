package kd.cus.yw;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.bos.servicehelper.workflow.MessageCenterServiceHelper;
import kd.bos.util.HttpClientUtils;
import kd.bos.workflow.engine.msg.info.MessageInfo;
import kd.cus.api.LogBillUtils;
import kd.cus.api.LogEntity;
import kd.cus.api.LogUtils;
import kd.cus.api.ResultEntity;
import kd.cus.api.SpicCusConfig;
import kd.cus.api.ThrowableUtils;
import kd.cus.api.entity.PostMsgEntity;

public class PayStatusTask extends AbstractTask{
	
	private final static String START_STR = "开始传输";
	private final static String FAILURE_STR = "传输失败";
	private final static String NOW_STR = "正在传输";
	private final static String SUCCESS_STR = "传输成功";
	private final static String ERROR_STR = "解析失败";
	private final static String ADJUSTERROR_STR = "司库调用失败";
	private final static String OTHER_STR = "其他错误";

	private final static String SUCCESS_CODE = "1";
	private final static String FAILURE_CODE = "2";
	
	@Override
	public void execute(RequestContext arg0, Map<String, Object> arg1) throws KDException {
		//前台打印日志（往单据中写）
		LogEntity logResult = LogBillUtils.createLog("", "开始执行", "", "", "FKZLJGCX");
		//后台打印日志
		Date startDate = LogUtils.log(null, "FKZLJGCX", START_STR, "", "开始执行", null, null);
		try {
			List<ResultEntity> results = new ArrayList<>();
			List<String> list = new ArrayList<>();
			list.add("");
			list.add("流程中");
			list.add("已失败");
			list.add("已驳回");
			Map header = new HashMap<>();
			header.put("Content-Type", "application/json");
			Map body = new HashMap<>();
			Map sysHead = new HashMap();
			sysHead.put("SYSTEM_ID", "JTGXFS"); 
			sysHead.put("MESSAGE_CODE", "0002");
			sysHead.put("MESSAGE_TYPE", "QUERY");
			sysHead.put("SERVICE_CODE", "NIBS_PTMS");
			body.put("SYS_HEAD", sysHead);
			Map body1 = new HashMap();
			body1.put("erpSysNo", "JTGXFS");
			QFilter[] qFilters1 = { new QFilter("settletype.settlementtype", QCP.equals, "3").and(new QFilter("spic_success", QCP.equals, "是")).and(new QFilter("spic_result", QCP.in, list)) };
			DynamicObject[] dynamicObjects = BusinessDataServiceHelper.load("cas_paybill", "id, billno, spic_result, billstatus, spic_erpid, cashier, spic_skfksbyy, spic_success, spic_errorcode, paydate", qFilters1);
			PostMsgEntity postMsgEntity = new PostMsgEntity();
			postMsgEntity.setEntityNumber("cas_paybill");//单据标识名称
			Date date = new Date();
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
			String date1 = sdf.format(date);
			Date date2 = sdf.parse(date1);
			String sikuURL = SpicCusConfig.getSpicCusConfig().getSikuUrl();// 访问地址  https://10.70.163.200:30009/esb
			for (DynamicObject dynamicObject : dynamicObjects) {
				String jsonStr = "";
				String erpid = dynamicObject.getString("spic_erpid");
				body1.put("ERP_ID", erpid);
				body.put("BODY", body1);
				String jsonString = JSON.toJSONString(body);
				try {
//					jsonStr = HttpClientUtils.postjson("https://10.70.163.200:30009/esb", header, jsonString);
					jsonStr = HttpClientUtils.postjson(sikuURL, header, jsonString);
				} catch (IOException e) {
					//后台打印日志
					LogUtils.log(false, "FKZLJGCX", NOW_STR, "", "单据编号为" + dynamicObject.getString("billno") + "的付款单调用司库失败", startDate, e);
					continue;
				}
				JSONObject jsonObject = JSON.parseObject(jsonStr);
				if (jsonObject != null) {
					Map BODY = (Map)jsonObject.get("BODY");
					if (BODY != null) {
						String retCode = (String)BODY.get("retCode");
						Object retMsg = BODY.get("retMsg");
						String state = (String)BODY.get("state");
						if("000000".equals(retCode)) {//调用成功
							String[] pkids = new String[] {dynamicObject.getPkValue() + ""};
							postMsgEntity.setPkids(pkids);//日志发送的单据pkid
							postMsgEntity.setSenderName("司库");//发送方名称
							postMsgEntity.setTag("司库接口");//标签
							DynamicObject[] users = new DynamicObject[] {(DynamicObject) dynamicObject.get("cashier")};//该付款单的出纳
							postMsgEntity.setUsers(users);//日志的接收人
							if ("0".equals(state)) {//流程中
								dynamicObject.set("spic_result", "流程中");//司库付款状态
								dynamicObject.set("spic_skfksbyy", retMsg);//司库付款失败原因
								SaveServiceHelper.save(new DynamicObject[] {dynamicObject});
								LogUtils.log(true, "FKZLJGCX", NOW_STR, jsonString, "单据编号为" + dynamicObject.getString("billno") + "的付款单的付款状态为流程中\t" + jsonStr, startDate, null);
							} else if ("-1".equals(state)) {//已驳回
								dynamicObject.set("billstatus", "A");//修改单据状态为暂存
								dynamicObject.set("spic_result", "已驳回");//司库付款状态
								dynamicObject.set("spic_skfksbyy", retMsg);//司库付款失败原因
								dynamicObject.set("spic_success", "");//同步司库是否成功
								dynamicObject.set("spic_errorcode", "");//同步司库失败原因
								SaveServiceHelper.save(new DynamicObject[] {dynamicObject});
								postMsgEntity.setMsg("付款单司库付款状态已驳回\t" + retMsg);//发送的信息
								postMsgEntity.setTitle("付款单司库付款状态已驳回");//标题
								postMsg(postMsgEntity);
								LogUtils.log(true, "FKZLJGCX", NOW_STR, jsonString, "单据编号为" + dynamicObject.getString("billno") + "的付款单的付款状态为已驳回\t" + jsonStr, startDate, null);
							} else if ("1".equals(state)) {//已成功
								dynamicObject.set("billstatus", "D");//修改单据状态为已付款
								dynamicObject.set("spic_result", "已成功");
								dynamicObject.set("spic_skfksbyy", retMsg);//司库付款失败原因
								dynamicObject.set("paydate", date2);//付款日期
								SaveServiceHelper.save(new DynamicObject[] {dynamicObject});
								postMsgEntity.setMsg("付款单司库付款状态已成功\t" + retMsg);//发送的信息
								postMsgEntity.setTitle("付款单司库付款状态已成功");//标题
								postMsg(postMsgEntity);
								LogUtils.log(true, "FKZLJGCX", NOW_STR, jsonString, "单据编号为" + dynamicObject.getString("billno") + "的付款单的付款状态为已成功\t" + jsonStr, startDate, null);
							} else if ("2".equals(state)) {//已失败
								dynamicObject.set("billstatus", "C");//修改单据状态为已审核
								dynamicObject.set("spic_result", "已失败");
								dynamicObject.set("spic_skfksbyy", retMsg);//司库付款失败原因
								dynamicObject.set("spic_success", "");//同步司库是否成功
								dynamicObject.set("spic_errorcode", "");//同步司库失败原因
								SaveServiceHelper.save(new DynamicObject[] {dynamicObject});
								postMsgEntity.setMsg("付款单司库付款状态已失败\t" + retMsg);//发送的信息
								postMsgEntity.setTitle("付款单司库付款状态已失败");//标题
								postMsg(postMsgEntity);
								LogUtils.log(true, "FKZLJGCX", NOW_STR, jsonString, "单据编号为" + dynamicObject.getString("billno") + "的付款单的付款状态为已失败\t" + jsonStr, startDate, null);
							}
						} else {
							LogUtils.log(true, "FKZLJGCX", NOW_STR, jsonString, "司库返回  业务返回结果代码：" + retCode + "，返回信息描述：" + retMsg + "\t" + jsonStr, startDate, null);
						}
					}
				}
				
			}
			LogBillUtils.modifyLog(logResult, "1", "正常，执行结束", "");
			LogUtils.log(true, "FKZLJGCX", SUCCESS_STR, "", "正常，执行结束", startDate, null);
		} catch (Exception e) {
			LogBillUtils.modifyLog(logResult, "2", "其他错误" + ThrowableUtils.getStackTrace(e), "");
			LogUtils.log(false, "FKZLJGCX", FAILURE_STR, "", "其他错误", startDate, e);
		}
	}
	
	/**
	 * @param postMsgEntity 发送的消息的参数，如果不发送消息，请选择没有这个参数的方法
	 * @return
	 */
	public static void postMsg(PostMsgEntity postMsgEntity) {
		Arrays.stream(postMsgEntity.getPkids()).forEach(pkid->{
			//发送消息
			MessageInfo messageInfo = new MessageInfo();
			messageInfo.setType(MessageInfo.TYPE_MESSAGE);
			messageInfo.setTitle(postMsgEntity.getTitle());
			messageInfo.setUserIds(Arrays.stream(postMsgEntity.getUsers()).map(DynamicObject::getPkValue).map(n->(Long)n).collect(Collectors.toList()));
			messageInfo.setSenderName(postMsgEntity.getSenderName());
			messageInfo.setSenderId(1L);
			messageInfo.setTag(postMsgEntity.getTag());
//			messageInfo.setContentUrl("http://10.80.58.121:8000/ierp/index.html?formId="+ postMsgEntity.getEntityNumber() +"&pkId=" + postMsgEntity.getPkids());//测试环境
//			messageInfo.setContentUrl("http://10.80.58.52:8000/ierp/index.html?formId="+ postMsgEntity.getEntityNumber() +"&pkId=" + postMsgEntity.getPkids());//开发环境
			messageInfo.setContentUrl("http://localhost:8080/ierp/index.html?formId="+ postMsgEntity.getEntityNumber() +"&pkId=" + pkid);
			messageInfo.setContent(postMsgEntity.getMsg());
			MessageCenterServiceHelper.sendMessage(messageInfo);
		});
	}
	
}

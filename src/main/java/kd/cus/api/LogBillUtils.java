package kd.cus.api;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.stream.Collectors;

import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.CodeRuleServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.bos.servicehelper.workflow.MessageCenterServiceHelper;
import kd.bos.workflow.engine.msg.info.MessageInfo;
import kd.cus.api.entity.PostMsgEntity;

public class LogBillUtils {
private static String LOGLIST_BILLCODE = "spic_maindata_log";// 日志的基础资料编码
	
	/**
	 * 开始发送log日志，进行开始记录
	 * @param request 请求
	 * @param response 响应
	 * @param source 来源
	 * @param partnerid 伙伴唯一id
	 * @param interfacename 接口名称
	 * @return
	 */
	public static LogEntity createLog(String request, String response, String source,String partnerid,String interfacename) {
		SimpleDateFormat sdf = new SimpleDateFormat();// 格式化时间 
        sdf.applyPattern("yyyy-MM-dd HH:mm:ss a");// a为am/pm的标记  
		// 将开始信息发送到存储log的苍穹单据列表里面
        Date startDate = new Date();
		Long orgId = 0L;//RequestContext.get().getOrgId();
		DynamicObject dynamicObject = BusinessDataServiceHelper.newDynamicObject(LOGLIST_BILLCODE);
//		Object logPkid = dynamicObject.getPkValue();
		String logId = CodeRuleServiceHelper.getNumber(LOGLIST_BILLCODE, dynamicObject, String.valueOf(orgId));
		logId = "in"+logId;
		dynamicObject.set("number", logId);
		dynamicObject.set("enable", "1");
		dynamicObject.set("status", "C");
		dynamicObject.set("spic_maindata_state", "0");
		dynamicObject.set("spic_start_time", null == startDate?"":sdf.format(startDate));
		dynamicObject.set("spic_end_time", "");
		dynamicObject.set("spic_over_time", "");
		dynamicObject.set("spic_interface_name", interfacename);
		dynamicObject.set("spic_source", source);
		dynamicObject.set("spic_partnerid", partnerid);
		dynamicObject.set("spic_requestdata_txt_tag", request);
		dynamicObject.set("spic_responsedata_txt_tag", response);
		Object[] result = SaveServiceHelper.save(new DynamicObject[] {dynamicObject});
		return new LogEntity(logId, startDate);
	}
	
	/**
	 * 结束处理修改log日志
	 * @param logResult create函数返回值 包含日志id，以及创建日志时间
	 * @param state 状态 0开始 1正常 2出错
	 * @param response 响应
	 * @param source 来源
	 * @return
	 */
	public static String modifyLog(LogEntity logResult, String state, String response,String source) {
		SimpleDateFormat sdf = new SimpleDateFormat();// 格式化时间 
        sdf.applyPattern("yyyy-MM-dd HH:mm:ss a");// a为am/pm的标记  
		// 运行结束，将结果对开始的log日志进行修改
		if (null == logResult) {
			return "logResult不能为空";
		} else {
			Date endDate = new Date();
			String overTime = LogUtils.getOverTime(logResult.getNowDate(), endDate);
			QFilter qFilter = new QFilter("number", QCP.equals, logResult.getLogPkid());
			DynamicObject dynamicObject = BusinessDataServiceHelper.loadSingleFromCache(LOGLIST_BILLCODE,  new QFilter[] {qFilter});
			dynamicObject.set("enable", "1");
			dynamicObject.set("status", "C");
			//dynamicObject.set("number", logId);
			dynamicObject.set("spic_maindata_state", state);
			dynamicObject.set("spic_start_time", sdf.format(logResult.getNowDate()));
			dynamicObject.set("spic_end_time", sdf.format(endDate));
			dynamicObject.set("spic_over_time", overTime);
			//dynamicObject.set("spic_interface_name", funName);
			dynamicObject.set("spic_source", source);
//			dynamicObject.set("spic_partnerid", partnerid);
			//dynamicObject.set("spic_requestdata_txt_tag", request);
			dynamicObject.set("spic_responsedata_txt_tag", response);
			//OperationServiceHelper.executeOperate("save", LOGLIST_BILLCODE, new DynamicObject[] {dynamicObject}, OperateOption.create());
			Object[] result = SaveServiceHelper.save(new DynamicObject[] {dynamicObject});
			return null;
		}
	}

	/**
	 *
	 *
	 *
	 *
	 *
	 *
	 *
	 * @param logResult log日志参数信息，调用createlog返回的信息
	 * @param state state 状态 1正常 2出错
	 * @param response response 响应
	 * @param source 来源
	 * @param postMsgEntity 发送的消息的参数，如果不发送消息，请选择没有这个参数的方法
	 * @return
	 */
	public static String modifyLog(LogEntity logResult, String state, String response, String source, PostMsgEntity postMsgEntity) {
		//日志进行补全
//		QFilter qFilter = new QFilter("number",QCP.equals,postMsgEntity.get);
//		BusinessDataServiceHelper.loadSingle("spic_pre_warning","spic_warning_bill,spic_mul_user",);
		modifyLog(logResult,state,response,source);
		Arrays.stream(postMsgEntity.getPkids()).forEach(pkid->{
			//发送消息
			MessageInfo messageInfo = new MessageInfo();
			messageInfo.setType(MessageInfo.TYPE_MESSAGE);
			messageInfo.setTitle(postMsgEntity.getTitle());
			messageInfo.setUserIds(Arrays.stream(postMsgEntity.getUsers()).map(DynamicObject::getPkValue).map(n->(Long)n).collect(Collectors.toList()));
			messageInfo.setSenderName(postMsgEntity.getSenderName());
			messageInfo.setSenderId(1L);
//		messageInfo.setEntityNumber("");
//		messageInfo.setOperation("");
			messageInfo.setTag(postMsgEntity.getTag());
//			RequestContext e = RequestContext.get();
//			String contextUrl = System.getProperty("domain.contextUrl");
//			String tenantCode = System.getProperty("tenantCode");
			messageInfo.setContentUrl("http://10.80.58.52:8000/ierp/index.html?formId="+ postMsgEntity.getEntityNumber() +"&pkId=" + postMsgEntity.getPkids());
			messageInfo.setContent(postMsgEntity.getMsg());
			MessageCenterServiceHelper.sendMessage(messageInfo);
		});
		return state;
	}
}

package kd.cus.yw;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import kd.bos.bill.IBillWebApiPlugin;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.metadata.dynamicobject.DynamicObjectType;
import kd.bos.entity.api.ApiResult;
import kd.bos.entity.operate.result.IOperateInfo;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.entity.property.LongProp;
import kd.bos.entity.property.PKFieldProp;
import kd.bos.entity.property.VarcharProp;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.service.business.datamodel.DynamicFormModelProxy;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.DBServiceHelper;
import kd.bos.servicehelper.MetadataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.bos.servicehelper.workflow.MessageCenterServiceHelper;
import kd.bos.util.HttpClientUtils;
import kd.bos.workflow.engine.msg.info.MessageInfo;
import kd.cus.api.LogBillUtils;
import kd.cus.api.LogEntity;
import kd.cus.api.LogUtils;
import kd.cus.api.ResultEntity;
import kd.cus.api.SelectUtils;
import kd.cus.api.SpicCusConfig;
import kd.cus.api.entity.CustomException;
import kd.cus.api.entity.FilterEntity;
import kd.cus.api.entity.PostMsgEntity;

/**
 * 交易明细查询
 * @author dhf
 */
public class TransDetailBillPlugin implements IBillWebApiPlugin {
	
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
	public ApiResult doCustomService(Map<String, Object> params) {
		ApiResult apiResult = new ApiResult();
		apiResult.setSuccess(true);
		apiResult.setErrorCode("success");
		apiResult.setMessage("HelloWorld Success");
		apiResult.setData(null);
		//前台打印日志（往单据中写）
		LogEntity logResult = LogBillUtils.createLog("", "开始执行", "", "", "ZHMXCX");
		//后台打印日志
		Date startDate = LogUtils.log(null, "ZHMXCX", START_STR, "", "开始执行", null, null);
		try {
			DynamicObject[] accountbanks = null;
//											QFilter[] qFilters1 = { new QFilter("spic_usesk", QCP.equals, true)};
			QFilter[] qFilters1 = {new QFilter("bankaccountnumber", QCP.not_equals, "")};
			accountbanks = BusinessDataServiceHelper.load("bd_accountbanks", "bankaccountnumber, spic_tbdate", qFilters1);
			List<ResultEntity> results = new ArrayList<>();
			String strURL = "https://10.70.163.200:30009/esb";
			String jsonStr = null;
			Map header = new HashMap<>();
			header.put("Content-Type", "application/json");
			Map body = new HashMap<>();
			Map sysHead = new HashMap();
			sysHead.put("SYSTEM_ID", "JTGXFS"); 
			sysHead.put("MESSAGE_CODE", "0003");
			sysHead.put("MESSAGE_TYPE", "QUERY");
			sysHead.put("SERVICE_CODE", "NIBS_AIMS");
			body.put("SYS_HEAD", sysHead);
			Map body1 = new HashMap<>();
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
			Calendar calendar = Calendar.getInstance();
			calendar.add(Calendar.DAY_OF_MONTH, -1);
			Date date = calendar.getTime();
			String yesterday = sdf.format(date);
			calendar.add(Calendar.DAY_OF_MONTH, -86);
			Date date1 = calendar.getTime();
			String yesterdayJian86 = sdf.format(date1);//昨天的头85天
			Date yesterdayDate = sdf.parse(yesterday);
//							Date date1101 = sdf.parse("2020-11-01");
			Date date1101 = sdf.parse("2020-10-25");
			int a = (int)(yesterdayDate.getTime()-date1101.getTime())/(1000*3600*24);//昨天零点和11月01日零点的时间间隔
			List<String> partSuccessList = new ArrayList<>();
			List<String> dyErrorList = new ArrayList<>();
			List<String> noCompanyList = new ArrayList<>();
			
			PostMsgEntity postMsgEntity = new PostMsgEntity();
//							postMsgEntity.setEntityNumber("cas_paybill");// 单据标识名称
			postMsgEntity.setSenderName("司库接口");// 发送方名称
			postMsgEntity.setTag("司库接口");// 标签
			postMsgEntity.setTitle("账户明细同步结果");// 标题
			
			for (DynamicObject accountbank : accountbanks) {
				Date tbdate = accountbank.getDate("spic_tbdate");//已同步日期
				String bankaccountnumber = accountbank.getString("bankaccountnumber");//银行账号
				body1.put("accountNo", bankaccountnumber);
				if (tbdate != null) {//已同步日期不为空
//											String tbdate1 = sdf.format(tbdate);
//											if(yesterday.equals(tbdate1) ) {
//												continue;
//											}
//											calendar.setTime(tbdate);
//											int day = calendar.get(Calendar.DATE);
//											calendar.set(Calendar.DATE, day + 1);
//											Date date2 = calendar.getTime();
//											String tommrrow = sdf.format(date2);
					body1.put("startDate", yesterday);
					body1.put("endDate", yesterday);
				} else {//已同步日期为空
					if (a <= 84) {//昨天和11月01日的时间间隔小于等于85天
//										body1.put("startDate", "2020-11-01");
						body1.put("startDate", "2020-10-25");
					} else {//昨天和11月01日的时间间隔大于85天
						body1.put("startDate", yesterdayJian86);//昨天的头85天
					}
					body1.put("endDate", yesterday);
				}
				body.put("BODY", body1);
				List<Map> list = new ArrayList<>();
				JSONObject jsonObject = null;
				try {
					adjust(strURL, jsonStr, header, body, body1, list, bankaccountnumber, startDate);
				} catch(CustomException e) {
					String code = e.getCode();
					if ("6666".equals(code)) {//调用司库失败
						dyErrorList.add(e.getMsg());
					}
					continue;
				}catch (Exception e) {
//									try {
//										adjust(strURL, jsonStr, header, body, body1, list);
//									} catch (Exception e1) {
//										LogUtils.log(false, "ZHMXCX", NOW_STR, "", "银行账号为" + bankaccountnumber + "的银行账户调用司库失败", startDate, e1);
						continue;
//									}
				}
//								List<ResultEntity> resultEntitys = excute(list);
				List<String> noCompanyRecordIdList = new  ArrayList<>();
				Map map = excute(list, noCompanyRecordIdList);
				String str = "";
				
				if (noCompanyRecordIdList.size() != 0) {
					str += "银行账号为" + bankaccountnumber + "的银行账户中，";
					for(String noCompanyRecordId : noCompanyRecordIdList) {
						str += noCompanyRecordId + "\t";
					}
					str += "的明细没有对应的行政组织，未导入平台";
					noCompanyList.add(str);
				}
				List<ResultEntity> resultEntitys = (List<ResultEntity>)map.get("resultEntitys");
				Boolean allSuccess = true;
				for (ResultEntity resultEntity : resultEntitys) {
					if (SUCCESS_CODE.equals(resultEntity.getIsSuccess())) {
						allSuccess = false;
					}
				}
				String msg = "";
				if (allSuccess) {
					accountbank.set("spic_tbdate", sdf.parse(yesterday));
					SaveServiceHelper.save(new DynamicObject[] {accountbank});
					LogUtils.log(true, "ZHMXCX", NOW_STR, "", "银行账号为" + bankaccountnumber + "的银行账户的交易明细导入平台全部成功", startDate, null);
				} else {
					List<String> jyErrorRecordIdList = (List<String>)map.get("jyErrorRecordIdList");
					msg += "银行账号为" + bankaccountnumber + "的银行账户的交易明细导入平台部分成功\trecordId为";
					for (String recordId : jyErrorRecordIdList) {
						msg += recordId + "/t";
					}
					msg += "的交易明细同步失败";
					partSuccessList.add(msg);//部分成功的信息放到部分成功的列表中
					accountbank.set("spic_tbdate", sdf.parse(yesterday));
					SaveServiceHelper.save(new DynamicObject[] {accountbank});
					LogUtils.log(true, "ZHMXCX", NOW_STR, "", "银行账号为" + bankaccountnumber + "的银行账户的交易明细导入平台部分成功", startDate, null);
				}
			}
			String endMsg = "";
			if (dyErrorList.size() != 0) {//存在调用失败的账号 A账号调用失败 B账号调用失败
				for (String msg : dyErrorList) {
					endMsg += msg + "\t";
				}
			}
			if(noCompanyList.size() != 0) {//存在明细无对应行政组织的账号 A账号 1 2 无组织未导入 C账号 1 2 无组织未导入
				for (String msg : noCompanyList) {
					endMsg += msg + "\t";
				}
			}
			if (partSuccessList.size() != 0) {//存在部分成功的账号 A账号部分成功 1 2 3 失败 B账号部分成功 1 2 3失败
//								String[] pkids = new String[] { dataEntity.getPkValue() + "" };
//								postMsgEntity.setPkids(pkids);// 日志发送的单据pkid
//								String str = "";
//								for (String bankaccountnumber : partSuccessList) {//partSuccessList部分成功的银行账号列表
//									str += "银行账号为" + bankaccountnumber + "的银行账户的交易明细导入平台部分成功\t"; 
//								}
//								postMsgEntity.setMsg(str);
				for (String msg : partSuccessList) {
					endMsg += msg + "\t";
				}
			}
			if (!"".equals(endMsg)) {
//						String userId = RequestContext.get().getUserId();
				String jobNumber = SpicCusConfig.getSpicCusConfig().getJobNumber();
//				QFilter[] qFilters2 = { new QFilter("number", QCP.equals, "ID-000014") };//工号 目前是测试环境的张力维
				QFilter[] qFilters2 = { new QFilter("number", QCP.equals, jobNumber) };
				DynamicObject user = BusinessDataServiceHelper.loadSingle("bos_user", "", qFilters2);// 当前登录人
				
				DynamicObject[] users = new DynamicObject[] { user };// 该付款单的创建人
				postMsgEntity.setUsers(users);// 日志的接收人
				postMsgEntity.setMsg(endMsg);
				postMsg(postMsgEntity);
			}
			LogBillUtils.modifyLog(logResult, "1", "正常，执行结束", "");
			LogUtils.log(true, "ZHMXCX", SUCCESS_STR, "", "正常，执行结束", startDate, null);
		} catch (Exception e) {
			LogBillUtils.modifyLog(logResult, "2", "其他错误", "");
			LogUtils.log(false, "ZHMXCX", FAILURE_STR, "", "其他错误", startDate, e);
		}
		return apiResult;
	}
	
	/**
	 * @param postMsgEntity
	 *            发送的消息的参数，如果不发送消息，请选择没有这个参数的方法
	 * @return
	 */
	public static void postMsg(PostMsgEntity postMsgEntity) {
//		Arrays.stream(postMsgEntity.getPkids()).forEach(pkid -> {
			// 发送消息
			MessageInfo messageInfo = new MessageInfo();
			messageInfo.setType(MessageInfo.TYPE_MESSAGE);
			messageInfo.setTitle(postMsgEntity.getTitle());
			messageInfo.setUserIds(Arrays.stream(postMsgEntity.getUsers()).map(DynamicObject::getPkValue)
					.map(n -> (Long) n).collect(Collectors.toList()));
			messageInfo.setSenderName(postMsgEntity.getSenderName());
			messageInfo.setSenderId(1L);
			messageInfo.setTag(postMsgEntity.getTag());
//			messageInfo.setContentUrl("http://localhost:8080/ierp/index.html?formId=" + postMsgEntity.getEntityNumber()
//					+ "&pkId=" + pkid);
			messageInfo.setContent(postMsgEntity.getMsg());
			MessageCenterServiceHelper.sendMessage(messageInfo);
//		});
	}

	private void adjust(String strURL, String jsonStr, Map header, Map body, Map body1, List<Map> list, String bankaccountnumber, Date startDate)
			throws Exception {
		JSONObject jsonObject = new JSONObject();
		jsonObject = doExcute(strURL, jsonStr, header, body, list, bankaccountnumber, startDate);
		//走到此处说明rowCount、pageSize、currentPage不是空串
		Map BODY1 = (Map)jsonObject.get("BODY");
		if (BODY1 != null) {
			int rowCount = 0;
			int pageSize = 0;
			int currentPage = 0;
			String rowCount1 = (String)BODY1.get("rowCount");
			if (rowCount1 !=null && !"".equals(rowCount1)) {
				rowCount = Integer.parseInt(rowCount1);
			} 
			String pageSize1 = (String)BODY1.get("pageSize");
			if (pageSize1 !=null && !"".equals(pageSize1)) {
				pageSize = Integer.parseInt(pageSize1);
			} 
			String currentPage1 = (String)BODY1.get("currentPage");
			if (currentPage1 !=null && !"".equals(currentPage1)) {
				currentPage = Integer.parseInt(currentPage1);
			} 
			int chu = rowCount / pageSize;
			int yu = rowCount % pageSize;
			if (yu != 0) {
				chu += 1;
			}
			body1.put("pageSize", pageSize);
			for (int i = 0 ; i < chu - 1 ; i++) {
				currentPage += 1;
				body1.put("currentPage", currentPage);
				doExcute(strURL, jsonStr, header, body, list, bankaccountnumber, startDate);
			}
		}
	}
	
	private JSONObject doExcute(String strURL, String jsonStr, Map header, Map body, List<Map> list, String bankaccountnumber, Date startDate) throws Exception {
		String jsonString = JSON.toJSONString(body);
		String sikuURL = SpicCusConfig.getSpicCusConfig().getSikuUrl();// 访问地址  https://10.70.163.200:30009/esb
//		jsonStr = HttpClientUtils.postjson("https://10.70.163.200:30009/esb", header, jsonString);
		try {
			jsonStr = HttpClientUtils.postjson(sikuURL, header, jsonString);
		} catch (Exception e) {
			LogUtils.log(false, "ZHMXCX", NOW_STR, "", "银行账号为" + bankaccountnumber + "的银行账户调用司库失败", startDate, e);
			throw new CustomException("6666", "银行账号为" + bankaccountnumber + "的银行账户调用司库失败");
		}
		JSONObject jsonObject = JSON.parseObject(jsonStr);
		if (jsonObject != null) {
			Map BODY = (Map)jsonObject.get("BODY");
			if (BODY != null) {
				String retCode = (String)BODY.get("retCode");
				String retMsg = (String)BODY.get("retMsg");
				if ("000000".equals(retCode)) {//查询成功
					List<Map> dataList = (List<Map>)BODY.get("dataList");
					if (dataList != null) {
						list.addAll(dataList);
					}
					LogUtils.log(true, "ZHMXCX", NOW_STR, jsonString, "银行账号为" + bankaccountnumber + "查询明细成功，响应报文为" + jsonStr, startDate, null);
				} else {//查询失败
					LogUtils.log(true, "ZHMXCX", NOW_STR, jsonString, "银行账号为" + bankaccountnumber + "查询明细失败，响应报文为" + jsonStr, startDate, null);
					throw new Exception("银行账号为" + bankaccountnumber + "的银行账户查询明细失败，查询失败的原因:" + retMsg);
				}
			}
		}
		return jsonObject;
	}
	
	private Map excute(List<Map> list, List<String> noCompanyRecordIdList) throws Exception{
		List<DynamicObject> dataEntities = new ArrayList<>();
		List recordIdList = new ArrayList<>();
		List existrecordIdList = new ArrayList<>();
		List<FilterEntity> filterEntities = new ArrayList<>();// 传入数据
		Map<String, DynamicObject> map1 = new HashMap<>();// 放基础资料返回结果
		list.forEach(data -> {
			Object recordId = data.get("recordId");
			recordIdList.add(recordId);
			Object companyNumber = data.get("cltNo");//单位编码
			filterEntities.add(new FilterEntity("bos_adminorg", "number", (String)companyNumber));// 资产组织
			Object currencyNo = data.get("currencyNo");//币种
			filterEntities.add(new FilterEntity("bd_currency", "number", currencyNo + ""));// 币别
			Object accountNo = data.get("accountNo");
			filterEntities.add(new FilterEntity("bd_accountbanks", "bankaccountnumber", accountNo + ""));// 银行账户
		});
		map1 = SelectUtils.loadAll(map1, filterEntities);
		//查询苍穹上已经已经存在的交易明细
		DynamicObjectCollection dynamicObjectCollection = QueryServiceHelper.query("bei_transdetail", "spic_mxid",
				new QFilter[] { new QFilter("spic_mxid", QCP.in, recordIdList) });
		if (null != dynamicObjectCollection && dynamicObjectCollection.size() != 0) {//有已经存在的交易明细
			for (DynamicObject dynamicObject : dynamicObjectCollection) {
				existrecordIdList.add(dynamicObject.get("spic_mxid"));//把已经存在的交易明细的billno放入存在的billno列表中
			}
		}
		
		for (Map map : list) {
			Object recordId = map.get("recordId");//明细id
			if (existrecordIdList.contains(recordId)) {
				continue;
			} 
			Object ticketNumber = map.get("ticketNumber");//司库返回单据号
			Object companyNumber = map.get("cltNo");//单位编码
			Object bizdate = map.get("recordDate");//明细日期
			Object currencyNo = map.get("currencyNo");
			Object accountNo = map.get("accountNo");
			Object description = map.get("explain");//摘要
			String balanceDir = (String)map.get("balanceDir");//收入/支出
			Object amount = map.get("amount");//明细金额
			Object debitamount = null;
			Object creditamount = null;
			if ("1".equals(balanceDir)) {//支出
				debitamount = amount;//付款金额
				creditamount = 0;//收款金额
			} else if ("2".equals(balanceDir)) {//收入
				creditamount = amount;
				debitamount = 0;
			}
			Object transbalance = map.get("balance");//余额
			Object oppunit = map.get("opAccountName");//对方账户名
			Object oppbanknumber = map.get("opAccountNo");//对方账户
			Object oppbank = map.get("opBankName");//对方银行
			Object detailid = map.get("hostId");//银行流水号
//			Map<Class<?>, Object> services = new HashMap<>();
//			DynamicFormModelProxy model = new DynamicFormModelProxy("bei_transdetail", UUID.randomUUID().toString(),
//					services);
//			model.createNewData();
//			DynamicObject transDetail = model.getDataEntity();//创建一个赋完默认值的对象
			
			DynamicObjectType type = MetadataServiceHelper.getDataEntityType("bei_transdetail");
            Map<Class<?>, Object> services = new HashMap<>();
            DynamicFormModelProxy model = new DynamicFormModelProxy("bei_transdetail", UUID.randomUUID().toString(), services);
            model.createNewData();
            PKFieldProp pkProp = (PKFieldProp) type.getPrimaryKey();
            if (pkProp instanceof LongProp) {
                model.getDataEntity().set(pkProp, DBServiceHelper.genGlobalLongId());
            } else if (pkProp instanceof VarcharProp) {
                model.getDataEntity().set(pkProp, DBServiceHelper.genStringId());
            }
//            model.setValue("billname", contractname);
            DynamicObject transDetail = model.getDataEntity(true);
//            this_dy.set("billname", contractname);
//            model.updateCache();
//            pkValue = model.getDataEntity().getPkValue();

			DynamicObject company = map1.get("bos_adminorg" + "@_@" + "number" + "@_@" + companyNumber);
			if (company == null) {
				String recordId1 = (String)recordId;
				noCompanyRecordIdList.add(recordId1);
				continue;      		
			}
//			QFilter[] qFilters1 = { new QFilter("number", QCP.equals, "100000")};
//			DynamicObject company = BusinessDataServiceHelper.loadSingle("bos_adminorg", "", qFilters1);
			transDetail.set("company", company);//资金组织
			transDetail.set("spic_ticketNumber", ticketNumber);//司库返回单据号
			SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd");
			transDetail.set("bizdate", sdf1.parse((String)bizdate));//交易日期
			DynamicObject currency = map1.get("bd_currency" + "@_@" + "number" + "@_@" + currencyNo);
			transDetail.set("currency", currency);//币别
			
			DynamicObject accountbank = map1.get("bd_accountbanks" + "@_@" + "bankaccountnumber" + "@_@" + accountNo);
			transDetail.set("accountbank", accountbank);//银行账号
			transDetail.set("description", description);//摘要
			transDetail.set("debitamount", debitamount);//付款金额
			transDetail.set("creditamount", creditamount);//收款金额
			transDetail.set("transbalance", transbalance);//余额
			transDetail.set("oppunit", oppunit);//对方户名
			transDetail.set("oppbanknumber", oppbanknumber);//对方账号
			transDetail.set("oppbank", oppbank);//对方开户行
			transDetail.set("detailid", detailid);//交易流水号
			
			transDetail.set("spic_mxid", recordId);//司库返回明细ID
			dataEntities.add(transDetail);
		}
		List<ResultEntity> resultEntitys = new ArrayList<>();
		Map map = new HashMap();
		if (dataEntities.size() > 0) {
			OperationResult operationResult = OperationServiceHelper.executeOperate("save", "bei_transdetail",
					dataEntities.toArray(new DynamicObject[dataEntities.size()]), OperateOption.create());

			List<Object> successPkIds = operationResult.getSuccessPkIds();
			List<IOperateInfo> OperateInfos = operationResult.getAllErrorOrValidateInfo();
			
			Map<Object, String> resultErrMap = new HashMap<>();
			Set<String> jyErrorSet = new HashSet<>();//校验失败的Set
			for (IOperateInfo operateInfo:OperateInfos) {
				jyErrorSet.add((String)operateInfo.getPkValue());
				if (null == resultErrMap.get(operateInfo.getPkValue()) ){
					resultErrMap.put(operateInfo.getPkValue(),operateInfo.getMessage());
				}else {
					resultErrMap.put(operateInfo.getPkValue(),resultErrMap.get(operateInfo.getPkValue()) +  operateInfo.getMessage());
				}
			}
			List<String> jyErrorRecordIdList = new ArrayList<>();
			for (String pk : jyErrorSet) {
				if (pk != null) {
					for (DynamicObject transDetail : dataEntities) {
						if (transDetail != null) {
							if (pk.equals(transDetail.getPkValue())) {
								String recordId = (String)transDetail.get("spic_mxid");
								jyErrorRecordIdList.add(recordId);
								break;
							}
						}
						
					}
				}
			}
			resultErrMap.entrySet().forEach(res->{
				resultEntitys.add(ResultEntity.PROCESS_ERROR(res.getValue()).setInitDate(res.getKey().toString(), "", ""));
			});
			Set<String> resultSucSet = new HashSet<String>();
			for (Object object : successPkIds) {
				resultSucSet.add(object.toString());
			}
			resultSucSet.forEach(res->{
				resultEntitys.add(ResultEntity.SUCCESS().setInitDate(res, "", ""));
			});
			map.put("jyErrorRecordIdList", jyErrorRecordIdList);
		}
		map.put("resultEntitys", resultEntitys);
		return map;
	}
}

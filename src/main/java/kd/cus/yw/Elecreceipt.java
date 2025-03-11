package kd.cus.yw;

import static org.hamcrest.CoreMatchers.nullValue;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.alibaba.druid.util.StringUtils;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import kd.bd.sbd.errorcode.SbdBaseErrorCode;
import kd.bos.bill.IBillWebApiPlugin;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.entity.MainEntityType;
import kd.bos.entity.api.ApiResult;
import kd.bos.entity.operate.result.IOperateInfo;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.entity.property.LongProp;
import kd.bos.entity.property.MuliLangTextProp;
import kd.bos.entity.property.PKFieldProp;
import kd.bos.entity.property.VarcharProp;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.service.business.datamodel.DynamicFormModelProxy;
import kd.bos.servicehelper.AttachmentServiceHelper;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.DBServiceHelper;
import kd.bos.servicehelper.MetadataServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.bos.util.HttpClientUtils;
import kd.cus.api.AttachmentUtil;
import kd.cus.api.FtpUtil;
import kd.cus.api.LogBillUtils;
import kd.cus.api.LogEntity;
import kd.cus.api.LogUtils;
import kd.cus.api.ResultEntity;
import kd.cus.api.SelectUtils;
import kd.cus.api.SpicCusConfig;
import kd.cus.api.entity.FilterEntity;

import kd.isc.iscb.platform.core.util.setter.MuliLangTextPropSetter;

/**
 * @author ZXR
 */
public class Elecreceipt implements IBillWebApiPlugin {

	private final static String START_STR = "开始传输";
	private final static String FAILURE_STR = "传输失败";
	private final static String SUCCESS_STR = "传输成功";
	private final static String ADJUSTERROR_STR = "司库调用失败";
	private final static String OTHER_STR = "其他错误";

	private final static String SUCCESS_CODE = "1";// 正常
	private final static String FAILURE_CODE = "2";// 出错

	@Override
	public ApiResult doCustomService(Map<String, Object> params) {
		// ----------------------webapi插件返回结果，用于测试---------------
		ApiResult apiResult = new ApiResult();
		apiResult.setSuccess(true);
		apiResult.setErrorCode("success");
		apiResult.setMessage("HelloWorld Success");
		apiResult.setData(null);

		financeCompany();
		bank();
		return apiResult;
	}
	

	/** 功能:通过请求报文和页码访问 http接口 跳转方法 */
	private void adjust(String strURL, String jsonStr, Map header, Map body, Map body1, List<Map> list)
			throws Exception {

		JSONObject jsonObject = new JSONObject();
		jsonObject = doExcute(strURL, jsonStr, header, body, list);

		LogUtils.log(false, "Elecreceipt_adjust方法", "正在传输", body.toString(), jsonStr, new Date(), null);

		if (null != jsonObject) {
			Map BODY = (Map) jsonObject.get("BODY");
			int rowCount = 0, pageSize = 0, currentPage = 0;
			// if (null != BODY && null != BODY.get("rowCount") && null !=
			// BODY.get("pageSize")
			// && null != BODY.get("currentPage")) {
			// }

			if (null != BODY && !"".equals(BODY.get("rowCount")) && !"".equals(BODY.get("pageSize"))
					&& !"".equals(BODY.get("currentPage"))) {
				rowCount = Integer.parseInt((String) BODY.get("rowCount"));// 数据总条数 
				pageSize = Integer.parseInt((String) BODY.get("pageSize"));// 分页大小100
				currentPage = Integer.parseInt((String) BODY.get("currentPage"));// 当前页1
			} else {
				throw new Exception("获取行号等数据异常,行数、页数、当前页是空");

			}
			int chu = rowCount / pageSize;
			int yu = rowCount % pageSize;
			if (yu != 0) {
				chu += 1;
			}
			body1.put("pageSize", pageSize);
			for (int i = 0; i < chu - 1; i++) {
				currentPage += 1;
				body1.put("currentPage", currentPage);
				doExcute(strURL, jsonStr, header, body, list);
			}
		}

	}

	/** 功能:连接https，返回所有的json数据，获得dataList中list; */
	private JSONObject doExcute(String strURL, String jsonStr, Map header, Map body, List<Map> list) throws Exception {

		String jsonString = JSON.toJSONString(body);

		String sikuURL = SpicCusConfig.getSpicCusConfig().getSikuUrl();// 访问地址
		// 如果MC中没取到值抛出异常
		if (StringUtils.isEmpty(sikuURL)) {
			LogUtils.log(false, "Elecreceipt", "MC中没获取到司库地址配置文件", "司库", "", null, null);
			throw new Exception("MC中没获取到司库地址配置文件");
		}
		jsonStr = HttpClientUtils.postjson(sikuURL, header, jsonString);

		if (jsonStr != null) {
			JSONObject jsonObject = JSON.parseObject(jsonStr);

			Map BODY = (Map) jsonObject.get("BODY");
			if (BODY != null) {
				List<Map> dataList = (List<Map>) BODY.get("dataList");
				if (dataList != null) {
					list.addAll(dataList);
				}
			}
			return jsonObject;

		}
		return null;
	}

	/**
	 * 功能: 保存操作
	 * 
	 * @param list
	 *            访问接口中datalist中的数据
	 * @param isType
	 *            A财务公司/B银行
	 * @return List<ResultEntity>多条接口
	 * @throws Exception
	 */
	private List<ResultEntity> excute(List<Map> list, String isType) throws Exception {

		List<DynamicObject> dataEntities = new ArrayList<>();
		List<String> noExistBillnoList = new ArrayList<>();// 已经存在的编码
		List<FilterEntity> filterEntities = new ArrayList<>();// 传入数据
		Map<String, DynamicObject> mapBasicData = new HashMap<>();// 放基础资料返回结果
		List<ResultEntity> resultEntitys = new ArrayList<>();
		if ("A".equals(isType)) {// 保存财务公司 的电子回单
			// 第一次循环放基础资料数据
			list.forEach(data -> {
				filterEntities.add(new FilterEntity("bei_elecreceipt", "spic_sourceid", (String) data.get("eno")));// 凭单号
				filterEntities.add(new FilterEntity("bos_adminorg", "name", (String) data.get("custName")));// 资金组织
				filterEntities.add(new FilterEntity("bd_currency", "number", (String) data.get("currencyNo")));// 币别
				filterEntities.add(new FilterEntity("bd_accountbanks", "number", (String) data.get("accNo"), null,
						"number,bankaccountnumber"));// 银行账户
			});
			mapBasicData = SelectUtils.loadAll(mapBasicData, filterEntities);

			// 第二次循环 把接收过来的值处理封装DynamicObject对象 ，用于保存
			for (Map data : list) {

				// ------处理编码----存在编码existBillnoList并返回，不存在编码放noExistBillnoList--------------
				if (null != data.get("eno") && !"".equals(data.get("eno"))) {
					DynamicObject dyEno = mapBasicData
							.get("bei_elecreceipt" + "@_@" + "spic_sourceid" + "@_@" + (String) data.get("eno"));
					if (null == dyEno) {
						noExistBillnoList.add((String) data.get("eno"));
					} else {
						resultEntitys.add(ResultEntity.PROCESS_ERROR((String) data.get("eno") + ":回单号已存在!")
								.setInitDate((String) data.get("eno"), "", ""));
						continue;
					}
				} else {
					// resultEntitys.add(ResultEntity.PROCESS_ERROR((String) data.get("eno") +
					// "回单号是空")
					// .setInitDate((String) data.get("eno"), "", ""));
					continue;
				}

				// ---------创建新单据-------------------------------------------------------------------
				// 获取主键
				MainEntityType dataEntityType = MetadataServiceHelper.getDataEntityType("bei_elecreceipt");
				PKFieldProp pkFieldProp = (PKFieldProp) dataEntityType.getPrimaryKey();

				Map<Class<?>, Object> services = new HashMap<>();
				DynamicFormModelProxy model = new DynamicFormModelProxy("bei_elecreceipt", UUID.randomUUID().toString(),
						services);
				model.createNewData();// 将默认值添加进去
				DynamicObject rec_dynamicObject = model.getDataEntity();
				// ----------------------------------------
				// 获得当前单据FID
				if (pkFieldProp instanceof LongProp) {
					model.getDataEntity().set(pkFieldProp, DBServiceHelper.genGlobalLongId());
				} else if (pkFieldProp instanceof VarcharProp) {
					model.getDataEntity().set(pkFieldProp, DBServiceHelper.genStringId());
				}
				Object pkID = model.getDataEntity().getPkValue();

				rec_dynamicObject.set("id", pkID);// PK

				// rec_dynamicObject.set("billno", data.get("eno"));//因为配置了电子回单编码规则，所以不用设置了

				// 资金组织 组织字段 所属客户
				DynamicObject companySet = mapBasicData
						.get("bos_adminorg" + "@_@" + "name" + "@_@" + data.get("custName"));
				if (null != companySet) {
					rec_dynamicObject.set("company", companySet);
				} else {
					resultEntitys
							.add(ResultEntity.PROCESS_ERROR("没找到资金组织").setInitDate((String) data.get("eno"), "", ""));
					continue;
				}

				// 交易日期 日期格式 交易日期
				if (null != data.get("txDate")) {
					SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
					rec_dynamicObject.set("bizdate", dateFormat.parse((String) data.get("txDate")));
				} else if (null != data.get("bookTime")) {
					SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
					rec_dynamicObject.set("bizdate", dateFormat.parse((String) data.get("bookTime")));
				} else {
					resultEntitys.add(ResultEntity.PROCESS_ERROR("交易日期或者明细交易时间没有数据")
							.setInitDate((String) data.get("eno"), "", ""));
					continue;
				}

				// 币别 基础资料 币种
				DynamicObject currencySet = mapBasicData
						.get("bd_currency" + "@_@" + "number" + "@_@" + data.get("currencyNo"));
				if (null != currencySet) {
					rec_dynamicObject.set("currency", currencySet);
				}
				// 银行账户 基础资料 本方账号
				DynamicObject accountbankSet = mapBasicData
						.get("bd_accountbanks" + "@_@" + "number" + "@_@" + data.get("accNo"));
				if (null != accountbankSet) {
					String bankaccountnumber = accountbankSet.getString("bankaccountnumber");
					if (!StringUtils.isEmpty(bankaccountnumber)) {
						rec_dynamicObject.set("accountbank", accountbankSet);
					}
				} else {
					resultEntitys.add(ResultEntity.PROCESS_ERROR((String) data.get("eno") + ":银行账号没找到!")
							.setInitDate((String) data.get("eno"), "", ""));
					continue;
				}
				// 明细交易时间 长日期格式 ：yyyy-MM-dd HH:mm:ss 业务发生日期
				String detaildatetime = (String) data.get("bookTime");
				if (!detaildatetime.isEmpty()) {
					int detaildateLength = detaildatetime.length();
					SimpleDateFormat detaildatetime1;
					if (detaildateLength > 12) {
						detaildatetime1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
					} else {
						detaildatetime1 = new SimpleDateFormat("yyyy-MM-dd");
					}
					rec_dynamicObject.set("detaildatetime", detaildatetime1.parse((String) data.get("bookTime")));
				}
				// 摘要 多语言文本
				MuliLangTextProp descriptionNameName = new MuliLangTextProp();
				MuliLangTextPropSetter descriptionNameSetter = new MuliLangTextPropSetter(descriptionNameName);
				descriptionNameSetter.setObjValue(rec_dynamicObject, "description", (String) data.get("explain"));
				// 付款金额 借记金额
				String strDEBIT = (String) data.get("amount_DEBIT");
				if (!StringUtils.isEmpty(strDEBIT)) {
					rec_dynamicObject.set("debitamount", BigDecimal.valueOf(Double.valueOf(strDEBIT)));
				}
				// 收款金额 贷记金额
				String strCREDIT = (String) data.get("amount_CREDIT");
				if (!StringUtils.isEmpty(strCREDIT)) {
					rec_dynamicObject.set("creditamount", BigDecimal.valueOf(Double.valueOf(strCREDIT)));
				}
				// 对方银行账号 对方账号
				rec_dynamicObject.set("oppbanknumber", data.get("opAccNo"));
				// 对方银行账号名称 对方户名
				rec_dynamicObject.set("oppbankname", data.get("opAccName"));
				// 验证码 授权码
				rec_dynamicObject.set("validcode", data.get("license"));
				// 付方账号 他行账号
				rec_dynamicObject.set("accno", data.get("ebkAccNo"));
				// 付方银行账号名称 他行户名
				rec_dynamicObject.set("accname", data.get("ebkAccountName"));
				// 来源类型(下拉框) A 财务回单/B 银行回单
				rec_dynamicObject.set("spic_sourceType", "A");
				// 来源编码
				rec_dynamicObject.set("spic_sourceid", data.get("eno"));
				// 来源业务种类 所属交易类型 业务种类
				rec_dynamicObject.set("spic_sourcebiztype", data.get("txName"));

				// ----------------------附件上传开始-----------------------
				if (null != data.get("FTPURL")) {// 如果txName字段里有url地址 将pdf下载
					LogUtils.log(false, "null != data.get(FTPURL)", "正在传输", data.toString(),
							(String) data.get("FTPURL"), new Date(), null);
					// fileserverurl FTPURL
					rec_dynamicObject.set("fileserverurl", data.get("FTPURL"));
					// 下载FTP文件，获得输入流和大小
					Map<String, Object> returnMap = downloadFtp((String) data.get("FTPURL"));
					if (null == returnMap) {
						LogUtils.log(false, "null == returnMap", "正在传输", data.toString(), (String) data.get("FTPURL"),
								new Date(), null);
						continue;
					}
					// Map<String, Object> returnMap = downloadFtp("\\111aaa\\111.pdf");
					// 创建附件变量
					List<Map<String, Object>> attachments = AttachmentServiceHelper.getAttachments("bei_elecreceipt",
							pkID, "attachmentpanel");
					Map<String, Object> map = new HashMap<>();

					// 文件命明赋值银行账号+日期+交易流水号+对方账号+借贷方向+金额
					String ftpName = (String) data.get("accNo") + (String) data.get("txDate")
							+ (String) data.get("opAccNo") + "出账";

					map.put("name", ftpName.replace(" ", "") + ".pdf");// 文件命明,去掉多有空格

					map.put("size", (long) returnMap.get("fileSize"));// 文件设置大小

					attachments.add(map);

					InputStream in = (java.io.InputStream) returnMap.get("inputStream");// 获得司库下载的数据流

					// int tem;
					// while ((tem = in.read()) != -1) {
					// System.out.print((char) tem);
					// }

					// 把文件流上传到苍穹系统
					AttachmentUtil.copyAttachments("bei_elecreceipt", pkID, "attachmentpanel", attachments, in);
				}
				// ----------------------附件上传结束-----------------------------------
				dataEntities.add(rec_dynamicObject);
			}
		} else {
			// 第一次循环放基础资料数据
			list.forEach(data -> {
				filterEntities
						.add(new FilterEntity("bei_elecreceipt", "spic_sourceid", (String) data.get("receiptNo")));// 银行
																													// //
																													// 回单编号
				filterEntities.add(new FilterEntity("bos_adminorg", "name", (String) data.get("cltName")));// 银行资金组织
				filterEntities.add(new FilterEntity("bd_currency", "number", (String) data.get("currencyNo")));// 币别
				filterEntities.add(new FilterEntity("bd_accountbanks", "number", (String) data.get("accountNo")));// 银行账户
				filterEntities.add(new FilterEntity("bd_finorginfo", "name", (String) data.get("bankName")));// 开户银行name
			});
			mapBasicData = SelectUtils.loadAll(mapBasicData, filterEntities);
			// 第二次循环 把接收过来的值处理封装DynamicObject对象 ，用于保存
			for (Map data : list) {

				// 银行的电子回单

				// ------处理编码----存在编码existBillnoList并返回，不存在编码放noExistBillnoList--------------
				if (null != data.get("receiptNo") && !"".equals(data.get("receiptNo"))) {
					DynamicObject dyReceiptNo = mapBasicData
							.get("bei_elecreceipt" + "@_@" + "spic_sourceid" + "@_@" + (String) data.get("receiptNo"));
					if (null == dyReceiptNo) {
						noExistBillnoList.add((String) data.get("receiptNo"));
					} else {
						resultEntitys.add(ResultEntity.PROCESS_ERROR((String) data.get("receiptNo") + ":回单号已存在!")
								.setInitDate((String) data.get("receiptNo"), "", ""));
						continue;
					}
				} else {
					// resultEntitys.add(ResultEntity.PROCESS_ERROR((String) data.get("eno") +
					// "回单号是空")
					// .setInitDate((String) data.get("receiptNo"), "", ""));
					continue;
				}

				// ---------创建新单据-------------------------------------------------------------------
				// 获取主键
				MainEntityType dataEntityType = MetadataServiceHelper.getDataEntityType("bei_elecreceipt");
				PKFieldProp pkFieldProp = (PKFieldProp) dataEntityType.getPrimaryKey();

				Map<Class<?>, Object> services = new HashMap<>();
				DynamicFormModelProxy model = new DynamicFormModelProxy("bei_elecreceipt", UUID.randomUUID().toString(),
						services);
				model.createNewData();// 将默认值添加进去
				DynamicObject rec_dynamicObject = model.getDataEntity();
				// -----------------获取PKIF-----------------------

				// 获得当前单据FID
				if (pkFieldProp instanceof LongProp) {
					model.getDataEntity().set(pkFieldProp, DBServiceHelper.genGlobalLongId());
				} else if (pkFieldProp instanceof VarcharProp) {
					model.getDataEntity().set(pkFieldProp, DBServiceHelper.genStringId());
				}
				Object pkID = model.getDataEntity().getPkValue();

				rec_dynamicObject.set("id", pkID);// PK
				// -----------------获取PKIF-------------------------------
				// 资金组织 组织字段 所属客户
				DynamicObject companySet = mapBasicData
						.get("bos_adminorg" + "@_@" + "name" + "@_@" + data.get("cltName"));
				if (null != companySet) {
					rec_dynamicObject.set("company", companySet);
				} else {
					resultEntitys.add(
							ResultEntity.PROCESS_ERROR("没找到资金组织").setInitDate((String) data.get("receiptNo"), "", ""));
					continue;
				}
				// 交易日期 日期格式 交易日期
				if (null != data.get("tradeDate")) {
					SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
					rec_dynamicObject.set("bizdate", dateFormat.parse((String) data.get("tradeDate")));
				}
				// 币别 基础资料 币种
				DynamicObject currencySet = mapBasicData
						.get("bd_currency" + "@_@" + "number" + "@_@" + data.get("currencyNo"));
				if (null != currencySet) {
					rec_dynamicObject.set("currency", currencySet);
				}
				// 银行账户 基础资料 本方账号
				DynamicObject accountbankSet = mapBasicData
						.get("bd_accountbanks" + "@_@" + "number" + "@_@" + data.get("accountNo"));
				if (null != accountbankSet) {
					String bankaccountnumber = accountbankSet.getString("bankaccountnumber");
					if (!StringUtils.isEmpty(bankaccountnumber)) {
						rec_dynamicObject.set("accountbank", accountbankSet);
					}
				} else {
					resultEntitys.add(ResultEntity.PROCESS_ERROR((String) data.get("receiptNo") + ":银行账号没找到!")
							.setInitDate((String) data.get("receiptNo"), "", ""));
					continue;
				}
				// 开户银行 基础资料 所属银行
				DynamicObject bankSet = mapBasicData
						.get("bd_finorginfo" + "@_@" + "name" + "@_@" + data.get("bankName"));
				if (null != bankSet) {
					rec_dynamicObject.set("bank", bankSet);
				}
				// 金额
				String strAmount = (String) data.get("tradeAmount");
				if (!StringUtils.isEmpty(strAmount)) {
					rec_dynamicObject.set("amount", BigDecimal.valueOf(Double.valueOf(strAmount)));
				}
				// 摘要 多语言文本
				MuliLangTextProp descriptionNameName = new MuliLangTextProp();
				MuliLangTextPropSetter descriptionNameSetter = new MuliLangTextPropSetter(descriptionNameName);
				descriptionNameSetter.setObjValue(rec_dynamicObject, "description", (String) data.get("notes"));
				// 对方银行账号 对方账号
				rec_dynamicObject.set("oppbanknumber", data.get("opAccNo"));
				// 对方银行账号名称 对方户名
				rec_dynamicObject.set("oppbankname", data.get("opAccName"));
				// 对方开户银行 对方开户行
				rec_dynamicObject.set("oppbank", data.get("opBankName"));
				// 电子回单号 银行交易流水id
				rec_dynamicObject.set("receiptno", data.get("bankId"));
				// 来源类型(下拉框) A 财务回单/B 银行回单
				rec_dynamicObject.set("spic_sourceType", "B");
				// 来源编码
				rec_dynamicObject.set("spic_sourceid", data.get("receiptNo"));
				// 来源业务种类 所属交易类型 业务种类
				rec_dynamicObject.set("spic_sourcebiztype", data.get("businessType"));

				// ----------------------附件上传开始-----------------------
				if (null != data.get("FTPURL")) {// 如果txName字段里有url地址 将pdf下载
					rec_dynamicObject.set("fileserverurl", data.get("FTPURL"));
					// 下载FTP文件，获得输入流和大小
					Map<String, Object> returnMap = downloadFtp((String) data.get("FTPURL"));
					if (null == returnMap) {
						continue;
					}
					// Map<String, Object> returnMap = downloadFtp("\\111aaa\\111.pdf");
					// 创建附件变量
					List<Map<String, Object>> attachments = AttachmentServiceHelper.getAttachments("bei_elecreceipt",
							pkID, "attachmentpanel");

					// 文件命明赋值银行账号+日期+交易流水号+对方账号+借贷方向+金额
					String ftpName = (String) data.get("accountNo") + (String) data.get("tradeDate")
							+ (String) data.get("opAccNo") + "出账";

					Map<String, Object> map = new HashMap<>();
					map.put("name", ftpName.replace(" ", "") + ".pdf");// 文件命明
					map.put("size", (long) returnMap.get("fileSize"));// 文件设置大小
					attachments.add(map);

					InputStream in = (java.io.InputStream) returnMap.get("inputStream");// 获得司库下载的数据流

					// int tem;
					// while ((tem = inputStream.read()) != -1) {
					// System.out.print((char) tem);
					// }

					// 把文件流上传到苍穹系统
					AttachmentUtil.copyAttachments("bei_elecreceipt", pkID, "attachmentpanel", attachments, in);
				}
				// ----------------------附件上传结束-----------------------------------
				dataEntities.add(rec_dynamicObject);
			}

		}
		if (noExistBillnoList.size() > 0) {
			OperationResult operationResult = OperationServiceHelper.executeOperate("save", "bei_elecreceipt",
					dataEntities.toArray(new DynamicObject[dataEntities.size()]), OperateOption.create());

			List<IOperateInfo> OperateInfos = operationResult.getAllErrorOrValidateInfo();
			List<Object> successPkIds = operationResult.getSuccessPkIds();

			// 错误
			Map<Object, String> resultErrMap = new HashMap<>();
			for (IOperateInfo operateInfo : OperateInfos) {
				if (null == resultErrMap.get(operateInfo.getPkValue())) {
					resultErrMap.put(operateInfo.getPkValue(), operateInfo.getMessage());
				} else {
					resultErrMap.put(operateInfo.getPkValue(),
							resultErrMap.get(operateInfo.getPkValue()) + operateInfo.getMessage());
				}
			}
			resultErrMap.entrySet().forEach(res -> {
				resultEntitys
						.add(ResultEntity.PROCESS_ERROR(res.getValue()).setInitDate(res.getKey().toString(), "", ""));
			});

			// 成功
			Set<String> resultSucSet = new HashSet<String>();
			for (Object object : successPkIds) {
				resultSucSet.add(object.toString());
			}
			resultSucSet.forEach(res -> {
				resultEntitys.add(ResultEntity.SUCCESS().setInitDate(res, "", ""));
			});

		}

		return resultEntitys;

	}

	/**
	 * 功能:财务公司电子回单主方法
	 * 
	 * @return
	 */
	public void financeCompany() {

		// 前台打印日志（往单据中写）
		LogEntity logResult = LogBillUtils.createLog("", "", "司库", "", "fcElecreceipt");
		// 后台打印日志
		Date startDate = LogUtils.log(null, "fcElecreceipt", "开始传输", "", "", null, null);

		StringBuffer exception = new StringBuffer();
		String resultName = "";
		try {
			// 如果MC中没取到值抛出异常
			// if (null == SpicCusConfig.getSpicCusConfig()) {
			// LogUtils.log(false, "fcElecreceipt", "MC中没获取到司库地址配置文件", "司库", "", null,
			// null);
			// throw new Exception("MC中没获取到司库地址配置文件");
			// }
			String strURL = SpicCusConfig.getSpicCusConfig().getSikuUrl();// 访问地址

			Map<String, String> header = new HashMap<>();
			header.put("Content-Type", "application/json");// 访问地址头部死值

			Map<String, String> sysHead = new HashMap<>();// 具体请求的哪个接口
			sysHead.put("SYSTEM_ID", "JTGXFS");
			sysHead.put("MESSAGE_CODE", "0001");
			sysHead.put("MESSAGE_TYPE", "QUERY");
			sysHead.put("SERVICE_CODE", "NIBS_SDM");

			// 根据“是否启用司库”字段，查询出银行账号
			DynamicObject[] accountbanks = null;
			// QFilter[] qFilters1 = { new QFilter("spic_usesk", QCP.equals, true) };
			QFilter[] qFilters1 = {};
			accountbanks = BusinessDataServiceHelper.load("bd_accountbanks", "bankaccountnumber, spic_tbdatefc",
					qFilters1);

			// 时间处理
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
			Calendar calendar = Calendar.getInstance();
			calendar.add(Calendar.DAY_OF_MONTH, -1);
			Date dateYesterday = calendar.getTime();
			String strYesterday = sdf.format(dateYesterday);// 当前时间的昨天

			if (null != accountbanks) {
				for (DynamicObject accountbank : accountbanks) {

					Map<String, String> body = new HashMap<>();// 根据业务请求哪些数据
					Map<String, Object> requestData = new HashMap<>();// {"SYS_HEAD":{},"BODY":{}}
					List<Map> list = new ArrayList<>();// 放dataList结果
					String jsonStr = null;// 访问url 返回的结果

					// List<ResultEntity> results = new ArrayList<>();// 错误结果遍历

					// 银行账号
					String bankaccountnumber = accountbank.getString("bankaccountnumber");

					body.put("accNo", bankaccountnumber);

					String strBasicDate = accountbank.getString("spic_tbdatefc");// 银行账户同步日期

					// 判断是否第一次进行同步 银行账号中回单时间有值
					if (strBasicDate != null && !"".equals(strBasicDate)) {// 已同步日期不为空

						Date dateBasicData = sdf.parse(strBasicDate);// 银行账户时间转换

						// 基础资料中时间和昨天是同一天,说明此条账号已经同步过了 查询的开始、结束时间是当天的前一天
						if (strYesterday.equals(sdf.format(dateBasicData))) {
							body.put("txDateStart", strYesterday);// 银行账户中的时间
							body.put("txDateEnd", strYesterday);// 今天的前一天 ，昨天
						} else {
							// 查询的时间是昨天以前，查询开始时间-结束
							calendar.setTime(dateBasicData);// 银行账户时间转换
							calendar.set(Calendar.DATE, calendar.get(Calendar.DATE) + 1);// 银行账户时间加一

							Date dateBasicData1 = calendar.getTime();// 获得银行账户时间
							String tommrrow = sdf.format(dateBasicData1);// 银行账户里的时间 转成String

							body.put("txDateStart", tommrrow);// 银行账户中的时间
							body.put("txDateEnd", strYesterday);// 今天的前一天 ，昨天
						}

					} else {// 第一次同步日期 ，银行账户中没值，值查前3个月的回单
						// Calendar calendar1 = Calendar.getInstance();
						// calendar1.add(Calendar.DAY_OF_MONTH, -61);
						// Date date1 = calendar.getTime();
						// String yesterdayJian60 = sdf.format(date1);// 昨天的头60天
						body.put("txDateStart", "2020-11-01");// 从2020-11-01开始获取
						body.put("txDateEnd", strYesterday);
					}

					// 将请求数据封装map中
					requestData.put("SYS_HEAD", sysHead);
					requestData.put("BODY", body);

					// 捕获访问司库http异常
					try {

						adjust(strURL, jsonStr, header, requestData, body, list);// 访问https地址，返回dataList结果放list
						LogUtils.log(false, "Elecreceipt", "正在传输", requestData.toString(), jsonStr, new Date(), null);
					} catch (Exception e) {
						try {
							// 抛异常再访问
							adjust(strURL, jsonStr, header, requestData, body, list);
						} catch (Exception e1) {
							exception.append("账号" + bankaccountnumber + ":" + e1.toString());
							// exception = exception + "账号" + bankaccountnumber + ":" + e1.toString();
							// 接口访问失败 后台打印日志
							LogUtils.log(false, "fcElecreceipt", ADJUSTERROR_STR,
									"账号" + bankaccountnumber + ":" + requestData != null ? requestData.toString() : "",
									jsonStr, startDate, e1);
							continue;
						}
					}
					// Boolean logSuccess = true;

					// 捕获苍穹系统保存操作异常
					try {
						Boolean allSuccess = false;

						List<ResultEntity> resultEntitys = new ArrayList<ResultEntity>();
						resultEntitys = excute(list, "A");// dataList中jaon数据 解析封装到DynamicObject对象中 并保存数据中

						for (ResultEntity resultEntity : resultEntitys) {
							resultName = "账号" + bankaccountnumber + ":" + resultName + resultEntity.getName();
							if (!SUCCESS_CODE.equals(resultEntity.getIsSuccess())) {
								allSuccess = true;
							}
						}

						if (allSuccess) {
							accountbank.set("spic_tbdatefc", sdf.parse(strYesterday));
							SaveServiceHelper.save(new DynamicObject[] { accountbank });
							// 修改前台日志为成功
							LogUtils.log(true, "fcElecreceipt", "司库" + SUCCESS_STR,
									(list != null ? list.toString() : "") + "账号" + bankaccountnumber, "成功", startDate,
									null);
						} else {
							accountbank.set("spic_tbdatefc", sdf.parse(strYesterday));
							SaveServiceHelper.save(new DynamicObject[] { accountbank });
							// exception = exception + "账号\t" + bankaccountnumber + ":\n" + resultName;
							boolean iseno = false;
							exception.append("账号" + bankaccountnumber + ":" + resultName);
							// for (Map m : list) {
							// if (null != m.get("eno") && !"".equals(m.get("eno"))) {
							// iseno = true;
							// }
							// }
							// if (iseno) {
							LogUtils.log(false, "fcElecreceipt", "司库" + FAILURE_STR,
									requestData.toString() + "\n:账号" + bankaccountnumber + ":" + list != null
											? list.toString()
											: "",
									resultName, startDate, null);
							// }

							continue;
						}

					} catch (Exception eSave) {
						// logSuccess =false;
						// exception = exception + "账号" + bankaccountnumber + ":" + eSave.toString();
						exception.append("账号" + bankaccountnumber + ":" + eSave.toString());
						// 保存操作异常
						LogUtils.log(false, "fcElecreceipt", "司库" + FAILURE_STR,
								requestData + "\n:账号" + bankaccountnumber + ":" + list != null ? list.toString() : "",
								"", startDate, eSave);
						continue;
					}

				}
			}
			if (null != exception && exception.length() > 0) {
				LogBillUtils.modifyLog(logResult, "2", exception.toString(), "司库");
			} else {
				LogBillUtils.modifyLog(logResult, "1", "正常执行结束", "司库");
			}
		} catch (Exception eAll) {
			LogUtils.log(false, "fcElecreceipt", OTHER_STR, "司库", "", startDate, eAll);
			LogBillUtils.modifyLog(logResult, "2", "其他错误:" + eAll.toString(), "司库");
		}

	}

	/**
	 * 功能: 银行 电子回单 主方法 {
	 * 
	 * @return
	 */
	public void bank() {

		// 前台打印日志（往单据中写）
		LogEntity logResult = LogBillUtils.createLog("", "", "司库", "", "bankElecreceipt");
		// 后台打印日志
		Date startDate = LogUtils.log(null, "bankElecreceipt", START_STR, "", "", null, null);

		StringBuffer exception = new StringBuffer();
		String resultName = "";
		try {
			// String strURL = System.getProperty("sk_address");// 访问司库地址

			// 如果MC中没取到值抛出异常
			// if (null == SpicCusConfig.getSpicCusConfig()) {
			// LogUtils.log(false, "Elecreceipt", "MC中没获取到司库地址配置文件", "司库", "", startDate,
			// null);
			// throw new Exception("MC中没获取到司库地址配置文件");
			// }
			String strURL = SpicCusConfig.getSpicCusConfig().getSikuUrl();// 访问地址
			// String strURL = "https://10.70.163.200:30009/esb";

			Map<String, String> header = new HashMap<>();
			header.put("Content-Type", "application/json");// 访问地址头部死值

			Map<String, String> sysHead = new HashMap<>();// 具体请求的哪个接口
			sysHead.put("SYSTEM_ID", "JTGXFS");
			sysHead.put("MESSAGE_CODE", "0001");
			sysHead.put("MESSAGE_TYPE", "QUERY");
			sysHead.put("SERVICE_CODE", "NIBS_RMP");

			// 根据“是否启用司库”字段，查询出银行账号
			DynamicObject[] accountbanks = null;
			// QFilter[] qFilters1 = { new QFilter("spic_usesk", QCP.equals, true) };
			QFilter[] qFilters1 = {};
			accountbanks = BusinessDataServiceHelper.load("bd_accountbanks", "bankaccountnumber, spic_tbdatebank",
					qFilters1);

			// 时间处理
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
			Calendar calendar = Calendar.getInstance();
			calendar.add(Calendar.DAY_OF_MONTH, -1);
			Date dateYesterday = calendar.getTime();
			String strYesterday = sdf.format(dateYesterday);// 当前时间的昨天
			if (null != accountbanks) {
				for (DynamicObject accountbank : accountbanks) {

					Map<String, String> body = new HashMap<>();// 根据业务请求哪些数据
					Map<String, Object> requestData = new HashMap<>();// {"SYS_HEAD":{},"BODY":{}}
					List<Map> list = new ArrayList<>();// 放dataList结果
					String jsonStr = null;// 访问url 返回的结果
					// List<ResultEntity> results = new ArrayList<>();// 错误结果遍历

					// 银行账号
					String bankaccountnumber = accountbank.getString("bankaccountnumber");
					body.put("accNo", bankaccountnumber);

					String strBasicDate = accountbank.getString("spic_tbdatebank");// 银行账户同步日期

					// 判断是否第一次进行同步
					if (strBasicDate != null && !"".equals(strBasicDate)) {// 已同步日期不为空

						// 银行账户时间+1天
						Date dateBasicData = sdf.parse(strBasicDate);// 银行账户时间转换

						// 基础资料中时间和昨天是同一天,说明此条账号已经同步过了
						if (strYesterday.equals(sdf.format(dateBasicData))) {
							body.put("txDateStart", strYesterday);// 银行账户中的时间
							body.put("txDateEnd", strYesterday);// 今天的前一天 ，昨天
						} else {
							// 查询的时间是昨天以前，查询开始时间-结束
							calendar.setTime(dateBasicData);// 银行账户时间转换
							calendar.set(Calendar.DATE, calendar.get(Calendar.DATE) + 1);// 银行账户时间加一

							Date dateBasicData1 = calendar.getTime();// 获得银行账户时间
							String tommrrow = sdf.format(dateBasicData1);// 银行账户里的时间 转成String

							body.put("txDateStart", tommrrow);// 银行账户中的时间
							body.put("txDateEnd", strYesterday);// 今天的前一天 ，昨天
						}

					} else {// 第一次同步日期 ，银行账户中没值，值查前3个月的回单

						// calendar.add(Calendar.DAY_OF_MONTH, -61);
						// Date date1 = calendar.getTime();
						// String yesterdayJian60 = sdf.format(date1);// 昨天的头60天
						body.put("txDateStart", "2020-11-01");// 从2020-11-01开始获取
						body.put("txDateEnd", strYesterday);

					}

					// 将请求数据封装map中
					requestData.put("SYS_HEAD", sysHead);
					requestData.put("BODY", body);

					// 捕获访问司库http异常
					try {
						adjust(strURL, jsonStr, header, requestData, body, list);// 访问https地址，返回dataList结果放list

					} catch (Exception e) {
						try {
							// 抛异常再访问
							adjust(strURL, jsonStr, header, requestData, body, list);
						} catch (Exception e1) {
							exception.append("账号" + bankaccountnumber + ":" + e1.toString());
							// 接口访问失败 后台打印日志
							LogUtils.log(false, "bankElecreceipt", ADJUSTERROR_STR,
									"账号" + bankaccountnumber + ":" + requestData != null ? requestData.toString() : "",
									jsonStr, startDate, e1);
							continue;
						}
					}

					// 捕获苍穹系统保存操作异常
					try {
						Boolean allSuccess = false;

						List<ResultEntity> resultEntitys = new ArrayList<ResultEntity>();
						// A是财务公司 电子回单，B是银行公司电子回单
						resultEntitys = excute(list, "B");// dataList中jaon数据 解析封装到DynamicObject对象中 并保存数据中

						for (ResultEntity resultEntity : resultEntitys) {
							resultName = "账号" + bankaccountnumber + ":" + resultName + resultEntity.getName();
							if (!SUCCESS_CODE.equals(resultEntity.getIsSuccess())) {
								allSuccess = true;
							}
						}

						if (allSuccess) {
							accountbank.set("spic_tbdatebank", sdf.parse(strYesterday));
							SaveServiceHelper.save(new DynamicObject[] { accountbank });
							// 修改前台日志为成功
							LogUtils.log(true, "bankElecreceipt", "司库" + SUCCESS_STR,
									(list != null ? list.toString() : "") + "账号" + bankaccountnumber, "成功", startDate,
									null);
						} else {
							accountbank.set("spic_tbdatebank", sdf.parse(strYesterday));
							SaveServiceHelper.save(new DynamicObject[] { accountbank });

							boolean iseno = false;
							exception.append("账号" + bankaccountnumber + ":" + resultName);
							// for (Map m : list) {
							// if (null != m.get("receiptNo") && !"".equals(m.get("receiptNo"))) {
							// iseno = true;
							// }
							// }
							// if (iseno) {
							LogUtils.log(false, "bankElecreceipt", "司库" + FAILURE_STR,
									requestData + "\n:账号" + bankaccountnumber + ":" + list != null ? list.toString()
											: "",
									resultName, startDate, null);
							// }
							continue;
						}

					} catch (Exception eSave) {
						exception.append("账号" + bankaccountnumber + ":" + eSave.toString());

						// 保存操作异常
						LogUtils.log(false, "bankElecreceipt", "司库" + FAILURE_STR,
								requestData + "\n:账号" + bankaccountnumber, "", startDate, eSave);

						continue;
					}
				}
			}
			if (null != exception && exception.length() > 0) {
				LogBillUtils.modifyLog(logResult, "2", exception.toString(), "司库");
			} else {
				LogBillUtils.modifyLog(logResult, "1", "正常执行结束", "司库");
			}
		} catch (Exception eAll) {
			LogUtils.log(false, "spic_tbdatebank", OTHER_STR, "司库", "", startDate, eAll);
			LogBillUtils.modifyLog(logResult, "2", "其他错误:" + eAll.toString(), "司库");
		}

	}

	// 从FTP服务器上 下载数据到苍穹系统
	public Map<String, Object> downloadFtp(String urlDownload) throws Exception {
		LogUtils.log(false, "进入downloadFtp方法", "正在传输", urlDownload, urlDownload, new Date(), null);
		// String ftpHost = "10.70.163.201";
		// String ftpUserName = "gdt";
		// String ftpPassword = "gdtftp";
		// int ftpPort = 21;

		// 如果MC中没取到值抛出异常
		if (null == SpicCusConfig.getSpicCusConfig()) {
			LogUtils.log(false, "fcElecreceipt", "MC中没获取到司库地址配置文件", "司库", "", null, null);
			throw new Exception("MC中没获取到司库地址配置文件");
		}
		String ftpHost = SpicCusConfig.getSpicCusConfig().getFtpHost();
		String ftpUserName = SpicCusConfig.getSpicCusConfig().getFtpUserName();
		String ftpPassword = SpicCusConfig.getSpicCusConfig().getFtpPassword();
		String ftpPortStr = SpicCusConfig.getSpicCusConfig().getFtpPort();
		int ftpPort = Integer.parseInt(ftpPortStr);

		// urlDownload = "\\111aaa\\111.pdf";// (司库下载的路径，必须用 FTP相对路径)
		// String ftpPathDownload = urlDownload.substring(0,
		// urlDownload.lastIndexOf("\\"));
		// String fileNameDownload = urlDownload.substring(urlDownload.lastIndexOf("\\")
		// + 1, urlDownload.length());

		String ftpPathDownload = urlDownload.substring(0, urlDownload.lastIndexOf("/"));
		String fileNameDownload = urlDownload.substring(urlDownload.lastIndexOf("/") + 1, urlDownload.length());

		return FtpUtil.downloadFtpFile(ftpHost, ftpUserName, ftpPassword, ftpPort, ftpPathDownload, fileNameDownload);

	}

	
	
}

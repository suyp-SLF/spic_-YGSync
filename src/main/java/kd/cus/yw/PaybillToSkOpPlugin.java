package kd.cus.yw;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dom4j.Document;
import org.dom4j.Element;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.ExtendedDataEntity;
import kd.bos.entity.plugin.AbstractOperationServicePlugIn;
import kd.bos.entity.plugin.AddValidatorsEventArgs;
import kd.bos.entity.plugin.PreparePropertysEventArgs;
import kd.bos.entity.plugin.args.BeforeOperationArgs;
import kd.bos.entity.validate.AbstractValidator;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.bos.servicehelper.workflow.MessageCenterServiceHelper;
import kd.bos.util.HttpClientUtils;
import kd.bos.util.StringUtils;
import kd.bos.workflow.engine.msg.info.MessageInfo;
import kd.cus.api.LogBillUtils;
import kd.cus.api.LogEntity;
import kd.cus.api.LogUtils;
import kd.cus.api.ResultEntity;
import kd.cus.api.SelectUtils;
import kd.cus.api.SpicCusConfig;
import kd.cus.api.ThrowableUtils;
import kd.cus.api.entity.FilterEntity;
import kd.cus.api.entity.PostMsgEntity;
import kd.cus.api.util.XmlUtils;

/**
 * @author dhf 付款指令
 */
public class PaybillToSkOpPlugin extends AbstractOperationServicePlugIn {

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
	public void onPreparePropertys(PreparePropertysEventArgs e) {
		super.onPreparePropertys(e);
		e.getFieldKeys().add("settletype");
		e.getFieldKeys().add("billstatus");
		e.getFieldKeys().add("payeracctcash");
		e.getFieldKeys().add("payeracctbank");
		e.getFieldKeys().add("payeebanknum");
		e.getFieldKeys().add("payeename");
		e.getFieldKeys().add("payeebankname");
		e.getFieldKeys().add("payeebank");
		e.getFieldKeys().add("actpayamt");
		e.getFieldKeys().add("expectdate");
		e.getFieldKeys().add("entry");
		e.getFieldKeys().add("entry.e_fundflowitem");
		e.getFieldKeys().add("description");
		e.getFieldKeys().add("settletnumber");
		e.getFieldKeys().add("payerbank");
		e.getFieldKeys().add("ispersonpay");
		e.getFieldKeys().add("spic_success");
		e.getFieldKeys().add("cashier");
		e.getFieldKeys().add("payeetype");
		e.getFieldKeys().add("paymentchannel");
		e.getFieldKeys().add("spic_errorcode");
		e.getFieldKeys().add("creator");
		e.getFieldKeys().add("payee");
		e.getFieldKeys().add("paydate");
		e.getFieldKeys().add("spic_zpfkqx");
		e.getFieldKeys().add("sourcetype");
		e.getFieldKeys().add("spic_source");
		e.getFieldKeys().add("spic_isground");
		e.getFieldKeys().add("spic_erpid");
		e.getFieldKeys().add("spic_usageother");
		e.getFieldKeys().add("spic_isignorewarn3");
		e.getFieldKeys().add("spic_first");
	}

	@Override
	public void beforeExecuteOperationTransaction(BeforeOperationArgs e) {
		super.beforeExecuteOperationTransaction(e);
		// 前台打印日志（往单据中写）
		LogEntity logResult = LogBillUtils.createLog("", "开始执行", "", "", "FKZLSC");
		// 后台打印日志
		Date startDate = LogUtils.log(null, "FKZLSC", START_STR, "", "开始执行", null, null);
		try {
			DynamicObject[] dataEntities = e.getDataEntities();
			List<DynamicObject> dynamicObjects = new ArrayList<DynamicObject>();
			List<FilterEntity> filterEntities = new ArrayList<>();// 传入数据
			Map<String, DynamicObject> map = new HashMap<>();// 放基础资料返回结果
			for (DynamicObject dataEntity : dataEntities) {
				String payeetype = dataEntity.getString("payeetype");// 收款人类型
				String payeebanknum1 = dataEntity.getString("payeebanknum");// 收款账号
				filterEntities.add(new FilterEntity("bd_accountbanks", "bankaccountnumber", payeebanknum1, null, "id, bank"));// 客户
			}
			map = SelectUtils.loadAllPRO(map, filterEntities);
			List<ResultEntity> results = new ArrayList<>();
			Map header = new HashMap<>();
			header.put("Content-Type", "application/json");
			Map body = new HashMap<>();
			Map sysHead = new HashMap();
			sysHead.put("SYSTEM_ID", "JTGXFS");
			sysHead.put("MESSAGE_CODE", "0001");
			sysHead.put("MESSAGE_TYPE", "SAVE");
			sysHead.put("SERVICE_CODE", "NIBS_PTMS");
			body.put("SYS_HEAD", sysHead);
			Map body1 = new HashMap();
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

			String url = SpicCusConfig.getSpicCusConfig().getNcUrl();
//			String url = "http://10.79.5.178:4437/servlet/ABC95599.Trans";
//			String url = "http://10.80.51.101:4437/servlet/ABC95599.Trans";
			HttpHeaders headers = new HttpHeaders();
			headers.add("HTTP", "//192.168.0.133/servlet/ABC95599.Trans HTTP/1.0");
			headers.add("Accept", "image/gif, image/x-xbitmap, image/jpeg, image/pjpeg, application/vnd.ms-powerpoint, application/vnd.ms-excel, application/msword, */*");
			headers.add("Referer", "HTTP://192.168.0.133/personRACrtLY.htm");
			headers.add("Accept-Language", "zh-cn");
			headers.add("Content-Type", "INFOSEC_SIGN/1.0");
			headers.add("Proxy-Connection", "Keep-Alive");
			headers.add("User-Agent", "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.0)");
			headers.add("Host", "192.168.0.133");

			headers.add("Pragma", "no-cache");
			
			PostMsgEntity postMsgEntity = new PostMsgEntity();
			postMsgEntity.setEntityNumber("cas_paybill");// 单据标识名称
			postMsgEntity.setSenderName("司库接口");// 发送方名称
			postMsgEntity.setTag("司库接口");// 标签
			postMsgEntity.setTitle("同步司库失败");// 标题
			String sikuURL = SpicCusConfig.getSpicCusConfig().getSikuUrl();// 访问地址  https://10.70.163.200:30009/esb
			for (DynamicObject dataEntity : dataEntities) {				
				String timestamp = "" + new Date().getTime();// 付款时间戳
				body1.put("timestamp", timestamp);
				Map plain = new HashMap();
				// String id = dataEntity.getString("id");
				long erpid = new Date().getTime();
				plain.put("ERP_ID", erpid);// 第三方系统付款指令ID
				Object isignorewarn3 = dataEntity.get("spic_isignorewarn3");// 是否忽略三级预警付款
				// 忽略三级预警付款
				if ("1".equals(isignorewarn3)) {
					String spic_erpid = dataEntity.getString("spic_erpid");
					plain.put("ERP_ID", spic_erpid);
				}
				plain.put("erpSysNo", "JTGXFS");// 第三方系统代码
				String payeename = dataEntity.getString("payeename");// 收款人名称
				plain.put("recAccountName", payeename);// 收款账户名称
				DynamicObject settletype = dataEntity.getDynamicObject("settletype");// 结算方式
				if (settletype != null) {
					// 结算方式是支票的时候
					if ("JSFS02".equals(settletype.getString("number")) || "JSFS03".equals(settletype.getString("number"))) {
						plain.put("payType", "03");// 付款类型 03支票付款
						DynamicObject org = dataEntity.getDynamicObject("org");// 付款人（公司）
						plain.put("payCltNo", org.getString("number"));// 付款单位
						String settletnumber = dataEntity.getString("settletnumber");// 结算号
						plain.put("bilNo", settletnumber);// 支票编号
						Date paydate = dataEntity.getDate("paydate");// 付款日期
						if (paydate != null) {
							String payDate = sdf.format(paydate);
							plain.put("payDate", payDate);// 出票日期
						} else {
							plain.put("payDate", "");
						}
						String zpfkqx = dataEntity.getString("spic_zpfkqx");// 支票付款期限
						plain.put("payLimit", zpfkqx);// 付款期限

						DynamicObject payerbank = dataEntity.getDynamicObject("payerbank");// 付款银行
						if (payerbank != null) {
							plain.put("payBank", payerbank.getString("name"));// 付款行名称
							plain.put("payBankNos", payerbank.getString("union_number"));// 出票行
						}
						// 结算方式是现金支票
						if ("JSFS02".equals(settletype.getString("number"))) {
							plain.put("chequeType", "Cash");// 支票类型
						} else if ("JSFS03".equals(settletype.getString("number"))) {// 结算方式是转账支票
							plain.put("chequeType", "Trans");// 支票类型
						}
					} else {// 结算方式不是支票
						String payeebanknum = dataEntity.getString("payeebanknum");// 收款账号
						plain.put("recAccountNo", payeebanknum);

						DynamicObject org = dataEntity.getDynamicObject("org");// 付款人（公司）

						// 结算方式是现金的时候
						if ("JSFS01".equals(settletype.getString("number"))) {
							plain.put("payType", "00");// 付款类型 00银行单笔付款
						} else {// 结算方式不是支票，也不是现金
							// 付款账户
							DynamicObject payeracctbank = dataEntity.getDynamicObject("payeracctbank");// 银行账号
							if (payeracctbank != null) {
								DynamicObject bank = payeracctbank.getDynamicObject("bank");// 付款账户开户行
								if (bank != null) {
									String number = bank.getString("number");// 付款账户开户行编码
									// 付款账户开户行是财务公司
									if ("FI-000002".equals(number) || "FI-000133".equals(number)) {
										String payeebanknum1 = dataEntity.getString("payeebanknum");// 收款账号
										DynamicObject accountbank = map.get("bd_accountbanks" + "@_@" + "bankaccountnumber" + "@_@" + payeebanknum1);// 收款账户
										if (accountbank != null) {
											DynamicObject bank1 = accountbank.getDynamicObject("bank");// 收款账户开户行
											if (bank1 != null) {
												String number1 = bank1.getString("number");// 收款账户开户行编码
												// 收款账户开户行是财务公司
												if ("FI-000002".equals(number1) || "FI-000133".equals(number1)) {
													plain.put("payType", "02");// 02财务公司内转付款
												} else {// 收款账户开户行不是财务公司
													plain.put("payType", "01");// 01财务公司代理付款
												}
											}
										} else {// 收款方是供应商或员工
											plain.put("payType", "01");
										}
									} else {// 付款账户开户行不是财务公司
										plain.put("payType", "00");// 00银行单笔付款
									}
								}
							}

						}

						String ispersonpay = dataEntity.getString("ispersonpay");// 是否对私
						if ("false".equals(ispersonpay)) {
							plain.put("isPrivate", "0");// 是否对私
						} else {
							plain.put("isPrivate", "1");// 是否对私
						}

						plain.put("isUrgent", "1");// 是否加急 默认传1
					}
				}

				DynamicObject payeracctcash = dataEntity.getDynamicObject("payeracctcash");// 现金账号
				DynamicObject payeracctbank = dataEntity.getDynamicObject("payeracctbank");// 银行账号
				// 结算方式是现金的时候
				if ("JSFS01".equals(settletype.getString("number"))) {
					plain.put("payResult", "1");// 付款结果
					if (payeracctcash != null) {
						plain.put("payAccountNo", payeracctcash.getString("number"));// 付款账号\出票人账号 现金账号
					} // 结算方式是汇兑
				} else if ("JSFS04".equals(settletype.getString("number")) || "JSFS05".equals(settletype.getString("number")) || "JSFS10".equals(settletype.getString("number")) || "JSFS11".equals(settletype.getString("number")) || "YNGJ_01".equals(settletype.getString("number"))) {
					if (payeracctbank != null) {
						plain.put("payAccountNo", payeracctbank.getString("bankaccountnumber"));// 付款账号\出票人账号 银行账号
					}
				} else {// 结算方式支票、汇票、信用证
					plain.put("payResult", "1");// 付款结果
					if (payeracctbank != null) {
						plain.put("payAccountNo", payeracctbank.getString("bankaccountnumber"));// 付款账号\出票人账号 银行账号
					}
				}

				String isground = dataEntity.getString("spic_isground");// 是否落地 0不落地 1落地
				plain.put("isGround", isground);
				plain.put("isIgnoreWarn3", isignorewarn3);// 是否忽略三级预警付款 0不忽略三级预警付款 1忽略三级预警付款 默认传1

				DynamicObject payeebank = dataEntity.getDynamicObject("payeebank");// 收款银行
				if (payeebank != null) {
					plain.put("recCnaps", payeebank.getString("union_number"));// 收方联行号
				} else {
					plain.put("recCnaps", "");// 收方联行号
				}

				BigDecimal actpayamt = dataEntity.getBigDecimal("actpayamt");// 付款金额
				plain.put("amount", actpayamt);// 付款金额\支票金额

				Date expectdate = dataEntity.getDate("expectdate");
				Date nowDate = new Date();
				String newDate1 = sdf.format(nowDate);// 当前日期字符串

				if (expectdate != null) {
					Date newDate2 = sdf.parse(newDate1);// 当前日期
					String actPayDate = sdf.format(expectdate);// 期望付款日期字符串
					Date actPayDate1 = sdf.parse(actPayDate);// 期望付款日期
					// 期望付款日期早于当前日期
					if (actPayDate1.before(newDate2)) {
						plain.put("actPayDate", newDate1);// 期望付款日
					} else {// 期望付款日期晚于或等于当前日期
						plain.put("actPayDate", actPayDate);// 期望付款日
					}
				} else {
					// Date auditdate = dataEntity.getDate("auditdate");//审核日期
					plain.put("actPayDate", newDate1);// 期望付款日
				}

				DynamicObjectCollection entry = dataEntity.getDynamicObjectCollection("entry");
				int rowCount = entry.getRowCount();
				if (rowCount > 0) {// 有分录
					DynamicObject firstEntryRow = entry.get(0);
					if (firstEntryRow != null) {
						DynamicObject fundflowitem = firstEntryRow.getDynamicObject("e_fundflowitem");// 资金用途
						if (fundflowitem !=  null) {
							plain.put("usage", fundflowitem.getString("name"));// 用途
							String fundflowtype = fundflowitem.getString("spic_fundflowtype");// 资金用途分类
							// 资金用途分类是其他
							if ("99".equals(fundflowtype)) {
								String usageother = firstEntryRow.getString("spic_usageother");// 获取补充说明
								plain.put("usageOther", usageother);// 国资委资金用途分类-其他描述
							}
							plain.put("usageType", fundflowtype);// 国资委资金用途分类
						} else {
							plain.put("usage", "");// 用途
							plain.put("usageType", "");// 国资委资金用途分类
						}
					} else {
						plain.put("usage", "");// 用途
						plain.put("usageType", "");// 国资委资金用途分类
					}
				} else {
					plain.put("usage", "");// 用途
					plain.put("usageType", "");// 国资委资金用途分类
				}

				plain.put("explain", dataEntity.getString("description"));// 摘要

				String jsonString = JSON.toJSONString(plain);
				String str = jsonString.replaceAll(":", "=").replaceAll(",", "&").replaceAll("\"", "").replaceAll("\\{", "").replaceAll("\\}", "");
				body1.put("plain", str);

				headers.add("Content-Length", String.valueOf(str.length()));// 要加签的字符串长度

				// 封装请求体
				HttpEntity<String> entity = new HttpEntity<>(str, headers);// str要加签的字符串
				// 发送请求
				RestTemplate restTemplate = new RestTemplate();
				restTemplate.getMessageConverters().set(1, new StringHttpMessageConverter(Charset.forName("GBK")));
				ResponseEntity<String> responseEntity = null;
				try {
					responseEntity = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
				} catch (Exception e1) {// 调用服务失败
					this.getOperationResult().setMessage("调用nc签名服务失败");// 提示信息
					this.getOperationResult().setSuccess(false);
					// 后台打印日志
					LogUtils.log(false, "FKZLSC", NOW_STR, "",
							"单据编号为" + dataEntity.getString("billno") + "的付款单调用nc签名服务失败,签名服务地址" + url, startDate, e1);
					continue;
				}
				String sign = "";
				String sign1 = "";
				if (null != responseEntity) {// 调用服务成功
					if (!HttpStatus.OK.equals(responseEntity.getStatusCode())) {// 签名失败
						this.getOperationResult().setMessage("调用nc签名服务签名失败");// 提示信息
						this.getOperationResult().setSuccess(false);
						LogUtils.log(true, "FKZLSC", NOW_STR, "", "单据编号为" + dataEntity.getString("billno") + "的付款单调用nc签名服务签名失败", startDate, null);
						continue;
					}
					String resultString = responseEntity.getBody();
					if (StringUtils.isNotEmpty(resultString) && resultString.contains("<result>0</result>")) {
						int startIndex = resultString.indexOf("<sign>");
						int endIndex = resultString.indexOf("</sign>");
						if (startIndex >= 0 && endIndex >= 0) {// 签名成功
							sign = resultString.substring(startIndex, endIndex).substring("<sign>".length());// 加密的签名
							Pattern pattern = Pattern.compile("\r|\n");
							Matcher matcher = pattern.matcher(sign);
							sign1 = matcher.replaceAll("");// 去换行符后的加密签名
						} else {// 签名失败
							this.getOperationResult().setMessage("调用nc签名服务签名失败");// 提示信息
							this.getOperationResult().setSuccess(false);
							LogUtils.log(true, "FKZLSC", NOW_STR, "", "单据编号为" + dataEntity.getString("billno") + "的付款单调用nc签名服务签名失败", startDate, null);
							continue;
						}
					} else {// 签名失败
						this.getOperationResult().setMessage("调用nc签名服务签名失败");// 提示信息
						this.getOperationResult().setSuccess(false);
						LogUtils.log(true, "FKZLSC", NOW_STR, "", "单据编号为" + dataEntity.getString("billno") + "的付款单调用nc签名服务签名失败", startDate, null);
						continue;
					}
				} else {// 调用服务失败
					this.getOperationResult().setMessage("调用nc签名服务失败");// 提示信息
					this.getOperationResult().setSuccess(false);
					// 后台打印日志
					LogUtils.log(false, "FKZLSC", NOW_STR, "", "单据编号为" + dataEntity.getString("billno") + "的付款单调用nc签名服务失败", startDate, null);
					continue;
				}

				body1.put("sign", sign1);
				body.put("BODY", body1);
				String jsonString1 = JSON.toJSONString(body);
				String jsonStr = "";
				try {
//					jsonStr = HttpClientUtils.postjson("https://10.70.163.200:30009/esb", header, jsonString1);
					jsonStr = HttpClientUtils.postjson(sikuURL, header, jsonString1);
				} catch (Exception e1) {
					// 后台打印日志
					this.getOperationResult().setMessage("调用司库失败");// 提示信息
					this.getOperationResult().setSuccess(false);
					LogUtils.log(false, "FKZLSC", NOW_STR, "", "单据编号为" + dataEntity.getString("billno") + "的付款单调用司库失败", startDate, e1);
					continue;
				}
				JSONObject jsonObject = JSON.parseObject(jsonStr);
				if (jsonObject != null) {
					Map BODY = (Map) jsonObject.get("BODY");
					if (BODY != null) {
						String retCode = (String) BODY.get("retCode");// 业务返回结果代码
						Object retMsg = BODY.get("retMsg");// 返回信息描述
						if ("".equals(retCode)) {//同步司库失败，返回值为空
							dataEntity.set("spic_errorcode", retMsg);
							dataEntity.set("spic_success", "否");
							SaveServiceHelper.save(new DynamicObject[] { dataEntity });
							this.getOperationResult().setMessage("同步司库失败，返回值为空");// 提示信息
							this.getOperationResult().setSuccess(false);
							String[] pkids = new String[] { dataEntity.getPkValue() + "" };
							postMsgEntity.setPkids(pkids);// 日志发送的单据pkid
							DynamicObject[] users = new DynamicObject[] { (DynamicObject) dataEntity.get("creator") };// 该付款单的创建人
							postMsgEntity.setUsers(users);// 日志的接收人
							postMsg(postMsgEntity);
							LogUtils.log(true, "FKZLSC", NOW_STR, jsonString1, "单据编号为" + dataEntity.getString("billno") + "的付款单同步司库失败，返回值为空\t" + jsonStr, startDate, null);
							continue;
						} else {//业务返回结果代码不为空串
							// 同步成功
							if ("000000".equals(retCode)) {
								if (settletype != null) {
									// 结算方式是汇兑
									if ("JSFS04".equals(settletype.getString("number")) || "JSFS05".equals(settletype.getString("number")) || "JSFS10".equals(settletype.getString("number")) || "JSFS11".equals(settletype.getString("number")) || "YNGJ_01".equals(settletype.getString("number"))) {
										dataEntity.set("billstatus", "E");// 银企处理中
									}
								}
								// 忽略三级预警付款
								if ("1".equals(isignorewarn3)) {
									dataEntity.set("spic_isignorewarn3", "0");// 不忽略三级预警
									String spicErpid = dataEntity.getString("spic_erpid");
									dataEntity.set("spic_erpid", spicErpid);
								} else {// 不忽略三级预警
									dataEntity.set("spic_erpid", erpid);// 司库erpid
								}
								dataEntity.set("spic_success", "是");
								dataEntity.set("spic_errorcode", "");
								SaveServiceHelper.save(new DynamicObject[] { dataEntity });
								this.getOperationResult().setMessage("同步司库成功");// 提示信息
								this.getOperationResult().setSuccess(false);
								LogUtils.log(true, "FKZLSC", NOW_STR, jsonString1, "单据编号为" + dataEntity.getString("billno") + "的付款单同步司库成功\t" + jsonStr, startDate, null);
							} else {// 同步失败 业务返回结果代码不为空串
								int retCode1 = Integer.parseInt(retCode);
								// 三级预警付款
								if (!"999999".equals(retCode)) {
									dataEntity.set("spic_isignorewarn3", "1");// 忽略三级预警付款
									String first = dataEntity.getString("spic_first");
									if ("1".equals(first)) {//是第一次
										dataEntity.set("spic_erpid", erpid);// 司库erpid
									}
									
									String retMsg1 = (String) retMsg;
									if ((retCode1 & 256) == 256) {
										retMsg1 += "\t触发摘要关键字预警";
									}
									if ((retCode1 & 128) == 128) {
										retMsg1 += "\t对私支付当日支付总额校验预警";
									}
									if ((retCode1 & 32) == 32) {
										retMsg1 += "\t灰名单校验预警";
									}
									if ((retCode1 & 16) == 16) {
										retMsg1 += "\t涉恐名单校验-支票付款预警";
									}
									if ((retCode1 & 8) == 8) {
										retMsg1 += "\t黑名单预警";
									}
									if ((retCode1 & 4) == 4) {
										retMsg1 += "\t疑似重复预警";
									}
									if ((retCode1 & 2) == 2) {
										retMsg1 += "\t对私付款预警";
									}
									if ((retCode1 & 1) == 1) {
										retMsg1 += "\t大额支付预警";
									}
									dataEntity.set("spic_first", "0");//修改为不是第一次
									dataEntity.set("spic_errorcode", retMsg1);
									this.getOperationResult().setMessage("同步司库失败:" + retMsg1);
									this.getOperationResult().setSuccess(false);
									postMsgEntity.setMsg("同步失败:" + retMsg1);// 发送的信息
								} else {// 不是三级预警付款
									dataEntity.set("spic_errorcode", retMsg);
									this.getOperationResult().setMessage("同步司库失败:" + retMsg);
									this.getOperationResult().setSuccess(false);
									postMsgEntity.setMsg("同步失败:" + retMsg);// 发送的信息
								}
								dataEntity.set("spic_success", "否");
								SaveServiceHelper.save(new DynamicObject[] { dataEntity });
								String[] pkids = new String[] { dataEntity.getPkValue() + "" };
								postMsgEntity.setPkids(pkids);// 日志发送的单据pkid
								DynamicObject[] users = new DynamicObject[] { (DynamicObject) dataEntity.get("creator") };// 该付款单的创建人
								postMsgEntity.setUsers(users);// 日志的接收人
								postMsg(postMsgEntity);
								LogUtils.log(true, "FKZLSC", NOW_STR, jsonString1, "单据编号为" + dataEntity.getString("billno") + "的付款单同步司库失败:" + retMsg + "\t" + jsonStr, startDate, null);
							}
						}
					}

				}
			}
			// }
			LogBillUtils.modifyLog(logResult, "1", "正常，执行结束", "");
			LogUtils.log(true, "FKZLSC", SUCCESS_STR, "", "正常，执行结束", startDate, null);
		} catch (Exception e1) {
			LogBillUtils.modifyLog(logResult, "2", "其他错误" + ThrowableUtils.getStackTrace(e1), "");
			LogUtils.log(false, "FKZLSC", FAILURE_STR, "", "其他错误", startDate, e1);
		}
	}

	/**
	 * @param postMsgEntity
	 *            发送的消息的参数，如果不发送消息，请选择没有这个参数的方法
	 * @return
	 */
	public static void postMsg(PostMsgEntity postMsgEntity) {
		Arrays.stream(postMsgEntity.getPkids()).forEach(pkid -> {
			// 发送消息
			MessageInfo messageInfo = new MessageInfo();
			messageInfo.setType(MessageInfo.TYPE_MESSAGE);
			messageInfo.setTitle(postMsgEntity.getTitle());
			messageInfo.setUserIds(Arrays.stream(postMsgEntity.getUsers()).map(DynamicObject::getPkValue)
					.map(n -> (Long) n).collect(Collectors.toList()));
			messageInfo.setSenderName(postMsgEntity.getSenderName());
			messageInfo.setSenderId(1L);
			messageInfo.setTag(postMsgEntity.getTag());
			// messageInfo.setContentUrl("http://10.80.58.121:8000/ierp/index.html?formId="+
			// postMsgEntity.getEntityNumber() +"&pkId=" + postMsgEntity.getPkids());//测试环境
			// messageInfo.setContentUrl("http://10.80.58.52:8000/ierp/index.html?formId="+
			// postMsgEntity.getEntityNumber() +"&pkId=" + postMsgEntity.getPkids());//开发环境
			messageInfo.setContentUrl("http://localhost:8080/ierp/index.html?formId=" + postMsgEntity.getEntityNumber()
					+ "&pkId=" + pkid);
			messageInfo.setContent(postMsgEntity.getMsg());
			MessageCenterServiceHelper.sendMessage(messageInfo);
		});
	}

	@Override
	public void onAddValidators(AddValidatorsEventArgs e) {
		super.onAddValidators(e);
		e.addValidator(new ApplySubmitValidator());
	}

	class ApplySubmitValidator extends AbstractValidator {
		@Override
		public void validate() {
			ExtendedDataEntity[] entities = this.getDataEntities();
			for (ExtendedDataEntity extendedDataEntity : entities) {
				DynamicObject dataEntity = extendedDataEntity.getDataEntity();
				DynamicObject settletype = dataEntity.getDynamicObject("settletype");
				String billno = dataEntity.getString("billno");// 单据编号
				String billstatus = dataEntity.getString("billstatus");// 单据状态
				String spicSuccess = dataEntity.getString("spic_success");// 同步司库是否成功
				String sourcetype = dataEntity.getString("sourcetype");// 来源系统
				String source = dataEntity.getString("spic_source");// 来源司库
				String isground = dataEntity.getString("spic_isground");// 是否落地
				if ("BE".equals(sourcetype)) {// 来源系统是银企直连 不能同步
					this.addErrorMessage(extendedDataEntity, "来源系统是银企直连，不能同步");
					continue;
				}
				if ("司库".equals(source)) {// 来源司库是司库 不能同步
					this.addErrorMessage(extendedDataEntity, "来源司库是司库，不能同步");
					continue;
				}
				if ("是".equals(spicSuccess)) {
					this.addErrorMessage(extendedDataEntity, "已同步，无需同步");
					continue;
				}
				if (settletype != null) {
					String settlementtype = settletype.getString("settlementtype");// 类别
					// 结算方式是现金
					if ("0".equals(settlementtype)) {
						if (!"D".equals(billstatus)) {// 单据状态不是已付款
							this.addErrorMessage(extendedDataEntity, "未付款，不能同步");
							continue;
						}
					} else if ("3".equals(settlementtype)) {// 结算方式是汇兑
						if ("1".equals(isground)) {// 是否落地为是
							if (!"B".equals(billstatus)) {// 单据状态不是已提交
								this.addErrorMessage(extendedDataEntity, "未提交，不能同步");
								continue;
							}
						} else {// 是否落地为否
							if (!"C".equals(billstatus)) {// 单据状态不是已审核
								this.addErrorMessage(extendedDataEntity, "未审核，不能同步");
								continue;
							}
						}
					} else {// 结算方式是支票、汇票、信用证
						if (!"D".equals(billstatus)) {// 单据状态不是已付款
							this.addErrorMessage(extendedDataEntity, "未付款，不能同步");
							continue;
						}
					}
				} else {
					this.addErrorMessage(extendedDataEntity, "未选择结算方式，不能同步");
					continue;
				}
				// 可以同步 修改出纳
				String userId = RequestContext.get().getUserId();
				QFilter[] qFilters1 = { new QFilter("id", QCP.equals, userId) };
				DynamicObject user = BusinessDataServiceHelper.loadSingle("bos_user", "name, number", qFilters1);// 当前登录人
				dataEntity.set("cashier", user);// 出纳
				SaveServiceHelper.save(new DynamicObject[] { dataEntity });
			}

		}

	}

}

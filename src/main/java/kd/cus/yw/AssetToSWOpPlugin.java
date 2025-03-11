package kd.cus.yw;

import java.io.StringReader;
import java.math.BigDecimal;
import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.rpc.ParameterMode;

import org.apache.axis.Constants;
import org.apache.axis.client.Call;
import org.apache.axis.client.Service;
import org.apache.axis.message.SOAPHeaderElement;
import org.dom4j.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

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
import kd.bos.workflow.engine.msg.info.MessageInfo;
import kd.cus.api.LogBillUtils;
import kd.cus.api.LogEntity;
import kd.cus.api.LogUtils;
import kd.cus.api.ResultEntity;
import kd.cus.api.SelectUtils;
import kd.cus.api.entity.FilterEntity;
import kd.cus.api.entity.PostMsgEntity;
import kd.cus.api.util.XmlUtils;

/**
 * @author dhf
 * 资产主数据集成
 */
public class AssetToSWOpPlugin extends AbstractOperationServicePlugIn {
	
	private final static String START_STR = "开始传输";
	private final static String FAILURE_STR = "传输失败";
	private final static String NOW_STR = "正在传输";
	private final static String SUCCESS_STR = "传输成功";
	private final static String ERROR_STR = "解析失败";
	private final static String ADJUSTERROR_STR = "司库调用失败";
	private final static String OTHER_STR = "其他错误";

	private final static String SUCCESS_CODE = "1";
	private final static String FAILURE_CODE = "2";
	
	private static String esbHeader ="<Esb>"
			+" <Route>"
			+" <Sender>cpi_jtcwgxpt</Sender>"
			+" <Time/>"
			+" <ServCode>cpi_ygsw.KdAssets.SynReq</ServCode>"
			+" <MsgId/>"
			+" <TransId/>"
			+" </Route>"
			+"</Esb> ";
	
	@Override
	public void onPreparePropertys(PreparePropertysEventArgs e) {
		super.onPreparePropertys(e);
		e.getFieldKeys().add("number");
		e.getFieldKeys().add("assetcat");
		e.getFieldKeys().add("originalval");
		e.getFieldKeys().add("entrychangebill");
		e.getFieldKeys().add("entrychangebill.changedate");
		e.getFieldKeys().add("spic_tbswgxresult");
		e.getFieldKeys().add("spic_tbswgxdefeatmsg");
		e.getFieldKeys().add("billstatus");
	}
	
	@Override
	public void beforeExecuteOperationTransaction(BeforeOperationArgs e) {
		super.beforeExecuteOperationTransaction(e);
		//前台打印日志（往单据中写）
		LogEntity logResult = LogBillUtils.createLog("", "开始执行", "", "", "ZCZSJJC");
		//后台打印日志
		Date startDate = LogUtils.log(null, "ZCZSJJC", START_STR, "", "开始执行", null, null);
		try {
			DynamicObject[] dataEntities = e.getDataEntities();
			List<DynamicObject> dynamicObjects = new ArrayList<DynamicObject>();
			List<FilterEntity> filterEntities = new ArrayList<>();// 传入数据
			Map<String, DynamicObject> map = new HashMap<>();// 放基础资料返回结果
			for (DynamicObject dataEntity : dataEntities) {
				String number = dataEntity.getString("number");//资产编码
				filterEntities.add(new FilterEntity("fa_card_real", "number", number, null, "number, assetunit, assetname, storeplace, headuseperson, usedate, assetamount"));// 开票登记
			}
			map = SelectUtils.loadAllPRO(map, filterEntities);
			String url = "http://10.80.56.99/proxy";
			Service serv = new Service();
			Call call = (Call) serv.createCall();
			call.removeProperty("xsd");
			call.setTargetEndpointAddress(url);
			// 添加相应的soap头信息
			//将字符串以流的形式存起来
			StringReader sr = new StringReader(esbHeader);
			InputSource is = new InputSource(sr);
			//生成w3c element
			Element esb = (Element) DocumentBuilderFactory.newInstance()
					.newDocumentBuilder().parse(is).getElementsByTagName("Esb").item(0);
			//将element放在头中
			call.addHeader(new SOAPHeaderElement(esb));
			call.setOperationName(new QName("http://www.ygsoft.com", "receiveData"));
			call.addParameter(new QName("http://www.ygsoft.com", "request"), Constants.XSD_STRING,ParameterMode.IN);
			List<ResultEntity> results = new ArrayList<>();
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
			for (DynamicObject dataEntity : dataEntities) {
				StringBuilder requestParam = new StringBuilder();
				String number = dataEntity.getString("number");//资产编码
				DynamicObject faCardReal = map.get("fa_card_real" + "@_@" + "number" + "@_@" + number);//实物卡片
				String compid = "", Zcmc = "", Zldz = "", syqmc = "", qdrq = "", Zclx = "", tdyz = "", tdmj = "", fczmj = "", Fcyz = "", Zcbh = "", zcrq = "";
				if (faCardReal != null) {
					DynamicObject assetunit = faCardReal.getDynamicObject("assetunit");//管理部门
					if (assetunit != null) {
						compid = assetunit.getString("number");//所属单位
					}
					Zcmc = faCardReal.getString("assetname");//资产名称
					DynamicObject storeplace = faCardReal.getDynamicObject("storeplace");//存放地点
					if (storeplace != null) {
						Zldz = storeplace.getString("name");//坐落地址
					}
					DynamicObject headuseperson = faCardReal.getDynamicObject("headuseperson");//使用人
					if (headuseperson != null) {
						syqmc = headuseperson.getString("name");//所有权人名称
					}
					//
					
					Date usedate = faCardReal.getDate("usedate");
					if (usedate != null) {
						qdrq = sdf.format(usedate);
					}
				}
				DynamicObject assetcat = dataEntity.getDynamicObject("assetcat");//资产类别
				String fullname = assetcat.getString("fullname");//长名称
				
				if (assetcat != null) {
					Zclx = assetcat.getString("number");
//					if ("160101".equals(assetcat.getString("number"))) {//土地资产
					if (fullname.contains("土地资产")) {
						tdyz = dataEntity.getString("originalval");//土地原值
						if (faCardReal != null) {
							tdmj = faCardReal.getString("assetamount");//土地面积
						}
//					} else if ("160102".equals(assetcat.getString("number")) || "16010201".equals(assetcat.getString("number")) || "16010202".equals(assetcat.getString("number"))) {
					} else if (fullname.contains("房屋、建筑物")) {
						//房屋、建筑物,房屋,建（构）筑物
//						Zclx = "01";//资产类型 01房产
						if (faCardReal != null) {
							fczmj = faCardReal.getString("assetamount");//房产总面积
						}
						Fcyz = dataEntity.getString("originalval");//房产原值
					}
				}
				Zcbh = number;//资产卡片编号
				DynamicObjectCollection entrychangebill = dataEntity.getDynamicObjectCollection("entrychangebill");//变更分录
				int rowCount = entrychangebill.getRowCount();
				if (rowCount > 0) {//有分录
					DynamicObject firstEntryRow = entrychangebill.get(0);
					if (firstEntryRow != null) {
						zcrq = firstEntryRow.getString("changedate");//权属转出日期
					}
				}
				requestParam.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><XM_DATA><ITEMS>");
				requestParam.append("<ITEM compid=\"" + compid + "\" zclx=\"" + Zclx + "\" zcbh=\"" + Zcbh + "\" zcmc=\"" + Zcmc + "\" zldz=\"" + Zldz + "\" syqmc=\"" + syqmc + "\" fczmj=\"" + fczmj + "\" fcyz=\"" + Fcyz + "\" tdyz=\"" + tdyz + "\" tdmj=\"" + tdmj + "\" qdrq=\"" + qdrq + "\" zcrq=\"" + zcrq +"\"/>");
				requestParam.append("</ITEMS></XM_DATA>");
				String requestParam1 = requestParam.toString();
				String result = "";
				try {
					result = (String) call.invoke(new Object[] { requestParam1 });
				} catch (RemoteException e1) {
					this.getOperationResult().setMessage("调用税务共享失败");//提示信息
					this.getOperationResult().setSuccess(false);
					LogUtils.log(false, "ZCZSJJC", NOW_STR, "", "资产编码为" + number + "的资产调用税务共享失败", startDate, e1);
					continue;
				}
				org.dom4j.Document doc = XmlUtils.getDocument(result);
				org.dom4j.Element rootElt = doc.getRootElement();
				String status = rootElt.element("STATUS").getText();
				String msg = rootElt.element("MSG").getText();
				//同步成功
				if ("0".equals(status)) {
					dataEntity.set("spic_tbswgxresult", "是");
					dataEntity.set("spic_tbswgxdefeatmsg", "");
					SaveServiceHelper.save(new DynamicObject[] {dataEntity});
					this.getOperationResult().setMessage("同步税务共享成功");//提示信息
					this.getOperationResult().setSuccess(false);
					LogUtils.log(true, "ZCZSJJC", NOW_STR, requestParam1, "资产编码为" + number + "的资产同步税务共享成功\t" + result, startDate, null);
				} else {//同步失败
					dataEntity.set("spic_tbswgxresult", "否");
					dataEntity.set("spic_tbswgxdefeatmsg", msg);
					SaveServiceHelper.save(new DynamicObject[] {dataEntity});
					this.getOperationResult().setMessage("同步税务共享失败:" + msg);
					this.getOperationResult().setSuccess(false);
					LogUtils.log(true, "ZCZSJJC", NOW_STR, requestParam1, "资产编码为" + number + "的资产同步税务共享失败:" + msg + "\t" + result, startDate, null);
				}
			}
			LogBillUtils.modifyLog(logResult, "1", "正常，执行结束", "");
			LogUtils.log(true, "ZCZSJJC", SUCCESS_STR, "", "正常，执行结束", startDate, null);
		} catch (Exception e1) {
			LogBillUtils.modifyLog(logResult, "2", "其他错误", "");
			LogUtils.log(false, "ZCZSJJC", FAILURE_STR, "", "其他错误", startDate, e1);
		}
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
			for (ExtendedDataEntity extendedDataEntity: entities) {
				DynamicObject dataEntity = extendedDataEntity.getDataEntity();
				String billstatus = dataEntity.getString("billstatus");//单据状态
				String tbswgxResult = dataEntity.getString("spic_tbswgxresult");//同步税务共享结果
				if (!"C".equals(billstatus)) {//单据状态不是已审核
					this.addErrorMessage(extendedDataEntity, "未审核，不能同步");
				}
				if ("是".equals(tbswgxResult)) {
					this.addErrorMessage(extendedDataEntity, "已同步，无需同步");
				}
			}
		}
		
	}


}

package kd.cus.yw;

import java.io.StringReader;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.rpc.ParameterMode;

import org.apache.axis.Constants;
import org.apache.axis.client.Call;
import org.apache.axis.client.Service;
import org.apache.axis.message.SOAPHeaderElement;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;


import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.entity.ExtendedDataEntity;
import kd.bos.entity.plugin.AbstractOperationServicePlugIn;
import kd.bos.entity.plugin.AddValidatorsEventArgs;
import kd.bos.entity.plugin.PreparePropertysEventArgs;
import kd.bos.entity.plugin.args.BeforeOperationArgs;
import kd.bos.entity.validate.AbstractValidator;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.cus.api.LogBillUtils;
import kd.cus.api.LogEntity;
import kd.cus.api.LogUtils;
import kd.cus.api.ResultEntity;
import kd.cus.api.util.XmlUtils;

/**
 * @author dhf
 * 资产主数据集成
 */
public class ProjectToSWOpPlugin extends AbstractOperationServicePlugIn {
	
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
			+" <ServCode>cpi_ygsw.ProjectMain.SynReq</ServCode>"
			+" <MsgId/>"
			+" <TransId/>"
			+" </Route>"
			+"</Esb> ";
	
	@Override
	public void onPreparePropertys(PreparePropertysEventArgs e) {
		super.onPreparePropertys(e);
			super.onPreparePropertys(e);
			e.getFieldKeys().add("spic_tbresult");
			e.getFieldKeys().add("spic_tbfailurereason");
			e.getFieldKeys().add("status");
			e.getFieldKeys().add("group");
			e.getFieldKeys().add("name");
			e.getFieldKeys().add("createorg");
			e.getFieldKeys().add("number");
	}
	
	@Override
	public void beforeExecuteOperationTransaction(BeforeOperationArgs e) {
		super.beforeExecuteOperationTransaction(e);
		//前台打印日志（往单据中写）
		LogEntity logResult = LogBillUtils.createLog("", "开始执行", "", "", "XMZSJJC");
		//后台打印日志
		Date startDate = LogUtils.log(null, "XMZSJJC", START_STR, "", "开始执行", null, null);
		try {
			DynamicObject[] dataEntities = e.getDataEntities();
			List<DynamicObject> dynamicObjects = new ArrayList<DynamicObject>();
			List<ResultEntity> results = new ArrayList<>();
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
			call.setOperationName(new QName("http://www.ygsoft.com", "syncProject"));
			call.addParameter(new QName("http://www.ygsoft.com", "request"), Constants.XSD_STRING,ParameterMode.IN);
			for (DynamicObject dataEntity : dataEntities) {
				StringBuilder requestParam = new StringBuilder();
				DynamicObject createorg = dataEntity.getDynamicObject("createorg");//创建组织
				String COMPID = createorg.getString("number");//所属单位
				String XMBH = dataEntity.getString("number");//项目编号
				String XMMC = dataEntity.getString("name");//项目名称
				DynamicObject group = dataEntity.getDynamicObject("group");//项目分类
				String DWXMLB = "";
				if (group != null) {
					DWXMLB = group.getString("number");//电商项目类别
				}
				requestParam.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><XM_DATA><ITEMS>");
				requestParam.append("<ITEM COMPID=\"" + COMPID + "\" XMBH=\"" + XMBH + "\" XMMC=\"" + XMMC + "\" DWXMLB=\"" + DWXMLB + "\"/>");
				requestParam.append("</ITEMS></XM_DATA>");
				String requestParam1 = requestParam.toString();
				String result = "";
				try {
					result = (String) call.invoke(new Object[] { requestParam1 });
				} catch (RemoteException e1) {
					this.getOperationResult().setMessage("调用税务共享失败");//提示信息
					this.getOperationResult().setSuccess(false);
					LogUtils.log(false, "XMZSJJC", NOW_STR, "", "项目编码为" + dataEntity.getString("number") + "的项目调用税务共享失败", startDate, e1);
					continue;
				}
				org.dom4j.Document doc = XmlUtils.getDocument(result);
				org.dom4j.Element rootElt = doc.getRootElement();
				String status = rootElt.element("STATUS").getText();
				String msg = rootElt.element("MSG").getText();
				//同步成功
				if ("0".equals(status)) {
					dataEntity.set("spic_tbresult", "是");
					dataEntity.set("spic_tbfailurereason", "");
					SaveServiceHelper.save(new DynamicObject[] {dataEntity});
					this.getOperationResult().setMessage("同步税务共享成功");//提示信息
					this.getOperationResult().setSuccess(false);
					LogUtils.log(true, "XMZSJJC", NOW_STR, requestParam1, "项目编码为" + dataEntity.getString("number") + "的项目同步税务共享成功\t" + result, startDate, null);
				} else {//同步失败
					dataEntity.set("spic_tbresult", "否");
					dataEntity.set("spic_tbfailurereason", msg);
					SaveServiceHelper.save(new DynamicObject[] {dataEntity});
					this.getOperationResult().setMessage("同步税务共享失败:" + msg);
					this.getOperationResult().setSuccess(false);
					LogUtils.log(true, "XMZSJJC", NOW_STR, requestParam1, "项目编码为" + dataEntity.getString("number") + "的项目同步税务共享失败:" + msg + "\t" + result, startDate, null);
				}
			}
			LogBillUtils.modifyLog(logResult, "1", "正常，执行结束", "");
			LogUtils.log(true, "XMZSJJC", SUCCESS_STR, "", "正常，执行结束", startDate, null);
		} catch (Exception e1) {
			LogBillUtils.modifyLog(logResult, "2", "其他错误", "");
			LogUtils.log(false, "XMZSJJC", FAILURE_STR, "", "其他错误", startDate, e1);
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
				String status = dataEntity.getString("status");//数据状态
				String tbResult = dataEntity.getString("spic_tbresult");//同步税务共享结果
				if (!"C".equals(status)) {//数据状态不是已审核
					this.addErrorMessage(extendedDataEntity, "未审核，不能同步");
				}
				if ("是".equals(tbResult)) {
					this.addErrorMessage(extendedDataEntity, "已同步，无需同步");
				}
			}
		}
		
	}


}

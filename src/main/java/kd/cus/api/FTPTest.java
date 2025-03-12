package kd.cus.api;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.SocketException;
import java.text.ParseException;
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

import org.stringtemplate.v4.compiler.STParser.mapExpr_return;

import com.alibaba.druid.util.StringUtils;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.carrotsearch.hppc.predicates.LongPredicate;

import kd.bos.bill.IBillWebApiPlugin;
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
import kd.bos.form.operate.New;
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
import kd.cus.api.LogBillUtils;
import kd.cus.api.LogEntity;
import kd.cus.api.LogUtils;
import kd.cus.api.ResultEntity;
import kd.cus.api.SelectUtils;
import kd.cus.api.AttachmentUtil;
import kd.cus.api.FtpUtil;
import kd.cus.api.entity.FilterEntity;

import kd.isc.iscb.platform.core.util.setter.MuliLangTextPropSetter;

public class FTPTest implements IBillWebApiPlugin {
	long fileSize = 0l;

	@Override
	public ApiResult doCustomService(Map<String, Object> params) {
		// ----------------------webapi插件返回结果，用于测试---------------
		ApiResult apiResult = new ApiResult();
		apiResult.setSuccess(true);
		apiResult.setErrorCode("success");
		apiResult.setMessage("HelloWorld Success");
		apiResult.setData(null);
		try {
			excute();
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return apiResult;
	}

	/**
	 * 功能: 保存操作
	 * 
	 * @param list
	 *            访问接口中datalist中的数据
	 * @param isType
	 *            A财务公司/B银行
	 * @return List<ResultEntity>多条接口
	 * @throws ParseException
	 * @throws IOException
	 * @throws Exception
	 */
	private void excute() throws ParseException, IOException {
		
//		 //获取主键
//		 MainEntityType dataEntityType = MetadataServiceHelper.getDataEntityType("bei_elecreceipt");
//		 PKFieldProp pkFieldProp = (PKFieldProp) dataEntityType.getPrimaryKey();
//		
//		 List<DynamicObject> receipts = new ArrayList<DynamicObject>();// 放数据
//		
//		 DynamicObject rec_dynamicObject = new DynamicObject();
//		 Map<Class<?>, Object> services = new HashMap<>();
//		 DynamicFormModelProxy model = new DynamicFormModelProxy("bei_elecreceipt",
//		 UUID.randomUUID().toString(),
//		 services);
//		 model.createNewData();// 将默认值添加进去
//		
//		 rec_dynamicObject = model.getDataEntity();
//		
//		 //获得当前单据FID
//		 if (pkFieldProp instanceof LongProp) {
//		 model.getDataEntity().set(pkFieldProp, DBServiceHelper.genGlobalLongId());
//		 } else if (pkFieldProp instanceof VarcharProp) {
//		 model.getDataEntity().set(pkFieldProp, DBServiceHelper.genStringId());
//		 }
//		 Object pk = model.getDataEntity().getPkValue();
//		 
//		 
//		 rec_dynamicObject.set("id", pk);// PK
//		 // 资金组织 组织字段
//		 DynamicObject companySet =
//		 BusinessDataServiceHelper.loadSingle("bos_adminorg", "",
//		 new QFilter[] { new QFilter("number", QCP.equals, "100000") });
//		 rec_dynamicObject.set("company", companySet);// ？？？是否这么赋值，我不确定
//		
//		 rec_dynamicObject.set("billno", "1234"); // 编码，如果有自己的编码规则，则不需要传值
//		//
//		 DynamicObject accountbankSet =
//		 BusinessDataServiceHelper.loadSingle("bd_accountbanks", "",
//		 new QFilter[] { new QFilter("number", QCP.equals, "1234567") });
//		 rec_dynamicObject.set("accountbank", accountbankSet);
//		
//		 // 交易日期 日期格式
//		 SimpleDateFormat detaildatetime1 = new SimpleDateFormat("yyyy-MM-dd");
//		 rec_dynamicObject.set("bizdate", detaildatetime1.parse("2020-10-10"));
		
//		 map.put("uid", "123");
//		
//		 map.put("lastModified", "999");
//		 map.put("url", "");

		// /spic/872529855609044992/202010/bei/bei_elecreceipt/1001243698203198464/attachments/111.pdf
		List<Map<String, Object>> attachments = AttachmentServiceHelper.getAttachments("cas_paybill",
				"999359104453458944", "attachmentpanel");
		
		// int size = downloadFtp(null).available();
		// System.out.print(size);
		
		// downloadFtp(null,fileSize);
		//从司库系统下来文件
		Map<String, Object> returnMap = downloadFtp(null);	
		Map<String, Object> map = new HashMap<>();
		map.put("name", "111.pdf");//文件命明
		map.put("size", (long)returnMap.get("fileSize"));//文件设置大小
		attachments.add(map);
		InputStream in = null;
		try {
		 in =(java.io.InputStream) returnMap.get("inputStream");//获得
		} catch (Exception e) {
			e.printStackTrace();
		
		}
	
		try {
			AttachmentUtil.copyAttachments("cas_paybill", "999359104453458944", "attachmentpanel", attachments, in);
		} catch (Exception e) {
			e.printStackTrace();
			// TODO: handle exception
		}

		// receipts.add(rec_dynamicObject);
		//
		// // 保存/更新到数据库
		// OperationResult operationResult =
		// OperationServiceHelper.executeOperate("save", "bei_elecreceipt",
		// receipts.toArray(new DynamicObject[receipts.size()]),
		// OperateOption.create());

	}

	// 从FTP服务器上 下载数据到本地
	public Map<String, Object> downloadFtp(String urlDownload) throws SocketException, IOException {
		
		String ftpHost = "127.0.0.1";
		String ftpUserName = "zou";
		String ftpPassword = "*******";
		int ftpPort = 21;
		
		

		urlDownload = "\\111aaa\\111.pdf";// (司库下载的路径，必须用 FTP相对路径)
		String ftpPathDownload = urlDownload.substring(0, urlDownload.lastIndexOf("\\"));
		String fileNameDownload = urlDownload.substring(urlDownload.lastIndexOf("\\") + 1, urlDownload.length());

		return  FtpUtil.downloadFtpFile(ftpHost, ftpUserName, ftpPassword, ftpPort, ftpPathDownload, fileNameDownload);
	
	}
}


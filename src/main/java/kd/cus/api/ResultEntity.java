package kd.cus.api;

import java.util.List;


/**
 * 结果枚举类
 * @author suyp
 *
 */
public class ResultEntity {
	
	private String code;
	private String name;
	private String isSuccess;
	private String source;
	private String codeid;
	private String version;
	
	
	public static ResultEntity REGISTEREDCODE_NONE() {
		return new ResultEntity("REGISTEREDCODE_NONE","未注册接口编码","1");
	}
	public static ResultEntity REGISTEREDCODE_DISABLE() {
		return new ResultEntity("REGISTEREDCODE_DISABLE","接口编码注册未启用","1");
	}
	public static ResultEntity CLASS_NOTFOUND() {
		return new ResultEntity("CLASS_NOTFOUND","未找到该解析方法","1");
	}
	public static ResultEntity FUNCTION_NOTFOUND() {
		return new ResultEntity("FUNCTION_NOTFOUND","未找到该解析方法","1");
	}
	public static ResultEntity FUNCTION_ERROR() {
		return new ResultEntity("FUNCTION_ERROR","解析方法出错","1");
	}
	public static ResultEntity XML_INITERROR() {
		return new ResultEntity("XML_INITERROR","初始化解析XML文件失败","1");
	}
	public static ResultEntity XML_DESPOSEERROR() {
		return new ResultEntity("XML_DESPOSEERROR","解析xml文件出错","1");
	}
	public static ResultEntity XML_NONE() {
		return new ResultEntity("XML_NONE","xml为空","1");
	}
	public static ResultEntity PROCESS_ERROR(String msg) {
		return new ResultEntity("PROCESS_ERROR",msg,"1");
	}
	public static ResultEntity PARTLY_SUCCESS() {
		return new ResultEntity("PARTLY_SUCCESS","存在失败","1");
	}
	public static ResultEntity SUCCESS() {
		return new ResultEntity("SUCCESS","成功","0");
	}
	
	public static ResultEntity ADJUST_SKERROR() {
		return new ResultEntity("ADJUST_SKERROR","调用司库失败","1");
	}
	
	private ResultEntity(String code,String name,String isSuccess) {
		this.code = code;
		this.name = name;
		this.isSuccess = isSuccess;
	}
	
	public String getSource() {
		return source;
	}

	/**
	 * 填写初始信息
	 * @param codeid datainfo标签里的参数  必填
	 * @param version datainfo标签李参数  必填
	 * @param source 用于在日志显示来源 最好填
	 * @return
	 */
	public ResultEntity setInitDate(String codeid, String version, String source) {
		this.codeid = codeid;
		this.version = version;
		this.source = source;
		return this;
	}

	public String getIsSuccess() {
		return isSuccess;
	}

	public String getName() {
		return this.name;
	}
		
	public String singleXml() {
		return "<DATAINFO codeid=\"" + this.codeid + "\" version=\"" + this.version + "\" status=\""+ this.isSuccess + "\" errorText=\""+ this.code + ":" + this.name +"\" />";
	}
	
	public static String resultXML(String syscodesyncode, String syscode, String uniqueid, List<ResultEntity> wsresults) {
		StringBuffer xmls = new StringBuffer("");
		wsresults.forEach(wsresult->{
			xmls.append(wsresult.singleXml());
		});
		return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><DATAINFOS SYSCODESYNCODE=\"" + syscodesyncode + "\" UNIQUEID=\"" + uniqueid + "\" SYSCODE=\"" + syscode + "\">" + xmls.toString() + "</DATAINFOS>";
	}
	
}

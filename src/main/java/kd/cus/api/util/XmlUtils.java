package kd.cus.api.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.XPath;
import org.dom4j.io.SAXReader;
import org.xml.sax.SAXException;

/**
 * 备用xml解析方法，用于解析带前缀的xml文件
 * @author suyp
 *
 */

public class XmlUtils {
	
	/**
	 * 
	 * @param doc
	 * @param code
	 * @return
	 */
	public static Element getDestElement(Document doc, String code) {
		HashMap<String, String> xmlMap = new HashMap<String, String>();
		xmlMap.put("soapenv", "http://schemas.xmlsoap.org/soap/envelope/");
		xmlMap.put("ser", "http://server.cxfserver.webservicedemo.pmi.jit.com.cn/");
		XPath xpath = doc.createXPath(code);//"//soapenv:Envelope/soapenv:Body/ser:organization/WebServiceOrg/ORGCODE"); // 要获取哪个节点，改这里就可以了
		xpath.setNamespaceURIs(xmlMap);
		return (Element) xpath.selectSingleNode(doc);
	}
	
	/**
	 * 解析xml
	 * @param xmlData
	 * @return
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws IOException
	 * @throws DocumentException 
	 */
	public static Document getDocument(String xmlData) throws ParserConfigurationException, SAXException, IOException, DocumentException {
		InputStream is = new ByteArrayInputStream(xmlData.getBytes("UTF-8"));
		SAXReader reader = new SAXReader();
		Document doc = reader.read(is);
		return doc;
	}
	
//	public static void main(String[] args) throws ParserConfigurationException, SAXException, IOException, DocumentException {
//		String string = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><DATAINFOS SYSCODESYNCODE=\"JTCWGXPTZZ\" UNIQUEID=\"055B8574F1694EEA958470CF2F96A403\" SYSCODE=\"ZZ\" COMPANY=\"三维天地科技有限公司\" SENDTIME=\"2020-07-09 09:40:14\"><DATAINFO><CODE REMARK=\"组织终身码\">100340</CODE><DESC1 REMARK=\"组织名称\">山东核电有限公司</DESC1><DESC2 REMARK=\"企业性质\">控股公司</DESC2><DESC3 REMARK=\"主营产业\">核电</DESC3><DESC4 REMARK=\"上级单位编码\">100000</DESC4><DESC5 REMARK=\"上级单位名称\">国家电力投资集团有限公司</DESC5><DESC6 REMARK=\"ERP机构编码\"></DESC6><DESC7 REMARK=\"法人代表\">吴放</DESC7><DESC8 REMARK=\"组织所在国家\">中国</DESC8><DESC9 REMARK=\"组织机构所在地\">山东省海阳市海阳核电厂</DESC9><DESC10 REMARK=\"组织机构代码\">75749004-8</DESC10><DESC11 REMARK=\"控股百分比\">0.65</DESC11><DESC12 REMARK=\"成立年月\">2013-10-29</DESC12><DESC13 REMARK=\"行政区域\">山东省</DESC13><DESC14 REMARK=\"状态\">正常</DESC14><DESC15 REMARK=\"所属二级单位编码\"></DESC15><DESC16 REMARK=\"所属二级单位名称\"></DESC16><DESC17 REMARK=\"所属三级单位编码\"></DESC17><DESC18 REMARK=\"办公电话\"></DESC18><DESC19 REMARK=\"描述\"></DESC19><DESC20 REMARK=\"邮编\"></DESC20><DESC21 REMARK=\"传真\"></DESC21><DESC22 REMARK=\"所属三级单位名称\"></DESC22><DESC23 REMARK=\"组织顺序号\"></DESC23><DESC24 REMARK=\"统一编码\"></DESC24><PARENTID REMARK=\"上级编码\">10991469</PARENTID><REMARK REMARK=\"备注\"></REMARK><UUID REMARK=\"MDM唯一标识\"></UUID><CODEID REMARK=\"MDM主键\">11019093</CODEID><FREEZEFLAG REMARK=\"数据状态\">0</FREEZEFLAG><MNEMONICCODE REMARK=\"助记码\">SDHDYXGS</MNEMONICCODE><RECORDERCODE REMARK=\"制单人编码\">admin</RECORDERCODE><RECORDERDESC REMARK=\"制单人名称\">超级管理员</RECORDERDESC><RECORDTIME REMARK=\"制单时间\">2018-11-15 20:20:34</RECORDTIME><RECORDERDCORP REMARK=\"制单人单位编码\">10001</RECORDERDCORP><SUBMITCORP REMARK=\"提报单位编码\">10001</SUBMITCORP><AUDITORCODE REMARK=\"审核人编码\">admin</AUDITORCODE><AUDITORDESC REMARK=\"审核人名称\">超级管理员</AUDITORDESC><AUDITTIME REMARK=\"审核时间\">2018-11-16 14:21:34</AUDITTIME><VERSION REMARK=\"主数据版本\">1</VERSION><SYSCODEVERSION REMARK=\"主数据模型版本\">9</SYSCODEVERSION><PARENTID REMARK=\"父结点ID\">10991469</PARENTID><PARENTCODE REMARK=\"父结点CODE\">100000</PARENTCODE><SPECIALITYCODES></SPECIALITYCODES></DATAINFO><DATAINFO><CODE>100590</CODE><DESC1>国核自仪系统工程有限公司</DESC1><DESC2>控股公司</DESC2><DESC3>核电</DESC3><DESC4>100000</DESC4><DESC5>国家电力投资集团有限公司</DESC5><DESC6></DESC6><DESC7>曹永振</DESC7><DESC8>中国</DESC8><DESC9>上海市闵行区江川东路428号</DESC9><DESC10>67270516-9</DESC10><DESC11>0.51</DESC11><DESC12>2015-12-02</DESC12><DESC13>上海市</DESC13><DESC14>正常</DESC14><DESC15></DESC15><DESC16></DESC16><DESC17></DESC17><DESC18></DESC18><DESC19></DESC19><DESC20></DESC20><DESC21></DESC21><DESC22></DESC22><DESC23></DESC23><DESC24></DESC24><PARENTID>10991469</PARENTID><REMARK ></REMARK><UUID ></UUID><CODEID >11018661</CODEID><FREEZEFLAG >0</FREEZEFLAG><MNEMONICCODE >GHZYXTGCYXGS</MNEMONICCODE><RECORDERCODE >admin</RECORDERCODE><RECORDERDESC >超级管理员</RECORDERDESC><RECORDTIME >2018-11-15 20:20:34</RECORDTIME><RECORDERDCORP >10001</RECORDERDCORP><SUBMITCORP >10001</SUBMITCORP><AUDITORCODE >admin</AUDITORCODE><AUDITORDESC >超级管理员</AUDITORDESC><AUDITTIME >2018-11-16 14:27:49</AUDITTIME><VERSION >1</VERSION><SYSCODEVERSION >9</SYSCODEVERSION><PARENTID >10991469</PARENTID><PARENTCODE >100000</PARENTCODE><SPECIALITYCODES></SPECIALITYCODES></DATAINFO></DATAINFOS>";
//		Document doc = getDocument(string);
//		
//		Element rootElt = doc.getRootElement();
//		
//		String e = rootElt.attribute("SYSCODESYNCODE").getText();
//		
//		List<Element> children = rootElt.elements("DATAINFO");
//		children.forEach(child->{
//			String codeid = null, version = null, code = null, name = null, parentCode = null;
//			codeid = child.element("CODEID").getText();
//			version = child.element("VERSION").getText();
//			code = child.element("CODE").getText();
//			name = child.element("DESC1").getText();
//			parentCode = child.element("PARENTCODE").getText();
//		});
//		System.out.println("");
//	}
}

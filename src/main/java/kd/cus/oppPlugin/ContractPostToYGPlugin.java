package kd.cus.oppPlugin;

import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.entity.operate.webapi.Save;
import kd.bos.entity.plugin.AbstractOperationServicePlugIn;
import kd.bos.entity.plugin.args.BeforeOperationArgs;
import kd.bos.logging.Log;
import kd.bos.logging.LogFactory;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.bos.util.HttpClientUtils;
import kd.cus.api.*;
import kd.cus.api.entity.FilterEntity;
import kd.cus.api.util.XmlUtils;
import org.apache.axis.Constants;
import org.apache.axis.client.Call;
import org.apache.axis.client.Service;
import org.apache.axis.message.SOAPHeaderElement;
import org.dom4j.DocumentException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.rpc.ParameterMode;
import javax.xml.rpc.ServiceException;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.io.StringReader;
import java.sql.Array;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class ContractPostToYGPlugin extends AbstractOperationServicePlugIn {

    private static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");

    private static Log logger = LogFactory.getLog(ContractPostToYGPlugin.class);

    private static String esbHeader = "<Esb>"
            + " <Route>"
            + " <Sender>cpi_jtcwgxpt</Sender>"
            + " <Time/>"
            + " <ServCode>cpi_ygsw.KdContract.SynReq</ServCode>"
            + " <MsgId/>"
            + " <TransId/>"
            + " </Route>"
            + "</Esb> ";

    private static String requestParam = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<MSG>\n" +
            " <VERSION>1</VERSION>\n" +
            " <FPLX>04</FPLX>\n" +
            " <FPDM>3400192130</FPDM>\n" +
            " <FPHM>19733265</FPHM>\n" +
            " <KPRQ>20200801</KPRQ>\n" +
            " <FPJE>194</FPJE>\n" +
            " <JYM>748750</JYM>\n" +
            " <USERNAME>5402</USERNAME>\n" +
            " <SENDTIME></SENDTIME>\n" +
            " <SIGN></SIGN>\n" +
            "</MSG>\n";




    @Override
    public void beforeExecuteOperationTransaction(BeforeOperationArgs e) {
//        LogBillUtils.createLog("请求发送到远光数据","","将合同数据发送到远光","无","远光合同")

        List<String> ids = Arrays.stream(e.getDataEntities()).map(DynamicObject::getPkValue).map(i -> i.toString()).collect(Collectors.toList());

        if (ids.size() > 0) {
            String name = e.getDataEntities()[0].getDynamicObjectType().getName();
            String respose = null;
            try {
                respose = post(save(ids, name));
                org.dom4j.Document doc = XmlUtils.getDocument(respose);
                org.dom4j.Element rootElt = doc.getRootElement();
                String status = rootElt.element("STATUS").getText();
                String msg = rootElt.element("MSG").getText();
                //同步成功
                if ("0".equals(status)) {
//                    dataEntity.set("spic_tbswgxresult", "同步成功");
//                    SaveServiceHelper.save(new DynamicObject[] {dataEntity});
//                    this.getOperationResult().setMessage("同步税务共享成功");//提示信息

                    this.getOperationResult().setMessage("税务同步成功");
//                    LogUtils.log(true, "ZCZSJJC", NOW_STR, "", "资产编码为" + number + "的资产同步税务共享成功", startDate, null);
                } else {//同步失败
//                    dataEntity.set("spic_tbswgxresult", "同步失败");
//                    dataEntity.set("spic_tbswgxdefeatmsg", msg);
//                    SaveServiceHelper.save(new DynamicObject[] {dataEntity});
//                    this.getOperationResult().setMessage("同步税务共享失败:" + msg);
                    e.setCancel(true);
                    this.getOperationResult().setMessage("税务同步失败:\r\n"+msg);
                    e.setCancelMessage(msg);
//                    LogUtils.log(true, "ZCZSJJC", NOW_STR, "", "资产编码为" + number + "的资产同步税务共享失败:" + msg, startDate, null);
                }

            } catch (Exception e1) {
//                e.setCancelMessage("发送到远光合同：" + e1.getMessage());
                e.setCancel(true);
//                LogBillUtils.
//                e1.printStackTrace();
                e.setCancelMessage(ThrowableUtils.getStackTrace(e1));
            }
        }
    }

    /**
     * xml进行拼装
     *
     * @param ids
     * @param entityNumber
     * @return
     * @throws ParserConfigurationException
     * @throws TransformerException
     */

    public static String save(List<String> ids, String entityNumber) throws ParserConfigurationException, TransformerException {
        List<FilterEntity> list = new ArrayList<>();
//        Map<String, DynamicObject> stringDynamicObjectMap = SelectUtils.loadAllPRO(new HashMap<>(), list);
        Map<Object, DynamicObject> stringDynamicObjectMap = BusinessDataServiceHelper.loadFromCache(entityNumber, new QFilter[]{new QFilter("id", QCP.in, ids)});
        Document document = CreateXmlFile.createDocument();
        Element root = CreateXmlFile.rootElement(document, "HT_DATA");
        Element items = CreateXmlFile.docCreateElement(document,"ITEMS");
        CreateXmlFile.parentAddChild(document,root,items);
        stringDynamicObjectMap.entrySet().forEach(item->{
//        ids.forEach(item -> {
            Element itemElement = CreateXmlFile.docCreateElement(document, "ITEM");
            String compid = "";
            if ("conm_purcontract".equals(entityNumber)){
                //采购合同  归属单位为甲方
                compid = item.getValue().getDynamicObject("org").getString("number");//归属单位
            }else if("conm_salcontract".equals(entityNumber)){
                //销售合同  归属方为客户
                compid = item.getValue().getDynamicObject("customer").getString("number");//归属单位
            }
            String yjhtfl = "";//item.getValue().getString("");//一级合同分类
            String ejhtfl = "";//item.getValue().getString("");//二级合同分类
            String sjhtfl = (null == item.getValue().getDynamicObject("spic_contracttype")?"":item.getValue().getDynamicObject("spic_contracttype").getString("number"));//item.getValue().getString("");//三级合同分类
            String htfl = "";//item.getValue().getString("");//应税凭证（合同分类）
            String htbh = item.getValue().getString("billno");//item.getValue().getString("");//合同编号
            String htmc = item.getValue().getString("billname");//item.getValue().getString("");//合同名称
            String qyf = "";//item.getValue().getString("");//签约方名称
            String qdrq = item.getValue().getDate("biztime") == null?null:sdf.format(item.getValue().getDate("biztime"));//item.getValue().getString("");//合同签订日期
            String ksrq = item.getValue().getDate("biztimebegin") == null?null:sdf.format(item.getValue().getDate("biztimebegin"));//item.getValue().getString("");//合同开始日期
            String jsrq = item.getValue().getDate("biztimeend") == null?null:sdf.format(item.getValue().getDate("biztimeend"));//item.getValue().getString("");//合同结束日期
            String htje = item.getValue().getBigDecimal("totalallamount") == null?"":item.getValue().getBigDecimal("totalallamount").setScale(2).toString();//item.getValue().getString("");//合同金额
            String jsje = item.getValue().getBigDecimal("spic_allsettleamount") == null?"":item.getValue().getBigDecimal("spic_allsettleamount").setScale(2).toString();//item.getValue().getString("");//结算金额
            String kjht = "";//item.getValue().getString("");//是否框架合同
            String czfnsrsbh = item.getValue().getString("spic_societycreditcode");//item.getValue().getString("");//承租方纳税人识别号（统一社会信用代码）
            String czfmc = "";//item.getValue().getString("");//承租方名称
            String zlsjq = "";//item.getValue().getString("");//租赁时间起
            String zlsjz = "";//item.getValue().getString("");//租赁时间止
            String fczckph = "";//item.getValue().getString("");//房产资产卡片号
            String fcmc = "";//item.getValue().getString("");//房产名称
            String czmj = "";//item.getValue().getString("");//出租面积
            String zjzje = item.getValue().getBigDecimal("totalamount") == null?"":item.getValue().getBigDecimal("totalamount").setScale(2).toString();//item.getValue().getString("");//租金总金额（不含税）
            String czfnsrsbh1 = item.getValue().getString("spic_societycreditcode");//item.getValue().getString("");//出租方纳税人识别号（统一社会信用代码）
            String czfmc1 = "";//item.getValue().getString("");//出租方名称
            String tdmc = "";//item.getValue().getString("");//土地名称
            String czmz = "";//item.getValue().getString("");//承租面积
            String bhsje = item.getValue().getBigDecimal("totalamount") == null?"":item.getValue().getBigDecimal("totalamount").setScale(2).toString();//不含税金额
            String se = item.getValue().getBigDecimal("totaltaxamount") == null?"":item.getValue().getBigDecimal("totaltaxamount").setScale(2).toString();//税额
            CreateXmlFile.setAttToElement(document, itemElement, "compid", compid);
            CreateXmlFile.setAttToElement(document, itemElement, "yjhtfl", yjhtfl);
            CreateXmlFile.setAttToElement(document, itemElement, "ejhtfl", ejhtfl);
            CreateXmlFile.setAttToElement(document, itemElement, "sjhtfl", sjhtfl);
            CreateXmlFile.setAttToElement(document, itemElement, "htfl", htfl);
            CreateXmlFile.setAttToElement(document, itemElement, "htbh", htbh);
            CreateXmlFile.setAttToElement(document, itemElement, "htmc", htmc);
            CreateXmlFile.setAttToElement(document, itemElement, "qyf", qyf);
            CreateXmlFile.setAttToElement(document, itemElement, "qdrq", qdrq);
            CreateXmlFile.setAttToElement(document, itemElement, "ksrq", ksrq);
            CreateXmlFile.setAttToElement(document, itemElement, "jsrq", jsrq);
            CreateXmlFile.setAttToElement(document, itemElement, "htje", htje);
            CreateXmlFile.setAttToElement(document, itemElement, "jsje", jsje);
            CreateXmlFile.setAttToElement(document, itemElement, "kjht", kjht);
            CreateXmlFile.setAttToElement(document, itemElement, "czfnsrsbh1", czfnsrsbh);
            CreateXmlFile.setAttToElement(document, itemElement, "czfmc1", czfmc);
            CreateXmlFile.setAttToElement(document, itemElement, "zlsjq", zlsjq);
            CreateXmlFile.setAttToElement(document, itemElement, "zlsjz", zlsjz);
            CreateXmlFile.setAttToElement(document, itemElement, "fczckph", fczckph);
            CreateXmlFile.setAttToElement(document, itemElement, "fcmc", fcmc);
            CreateXmlFile.setAttToElement(document, itemElement, "czmj", czmj);
            CreateXmlFile.setAttToElement(document, itemElement, "zjzje", zjzje);
            CreateXmlFile.setAttToElement(document, itemElement, "czfnsrsbh2", czfnsrsbh1);
            CreateXmlFile.setAttToElement(document, itemElement, "czfmc2", czfmc1);
            CreateXmlFile.setAttToElement(document, itemElement, "tdmc", tdmc);
            CreateXmlFile.setAttToElement(document, itemElement, "czmz", czmz);
            CreateXmlFile.setAttToElement(document, itemElement, "bhsje", bhsje);
            CreateXmlFile.setAttToElement(document, itemElement, "se", se);
            CreateXmlFile.parentAddChild(document, items, itemElement);
        });
        return CreateXmlFile.docToString(document);
    }

    public static void main(String[] args) throws ParserConfigurationException, TransformerException, ServiceException, SAXException, IOException {
        String xmlStr = save(Arrays.asList("123", "321"), "conm_purcontract");
        post(xmlStr);
    }

    /**
     * http post方法
     *
     * @param body
     * @return
     * @throws IOException
     */
    public static String post(String body) throws IOException, ParserConfigurationException, ServiceException, SAXException {
        logger.info("发送到远光合同==========================================\r\n" +
                "发送报文：" + body + "\r\n");
        String url = System.getProperty("esbUrl");
        Service serv = new Service();
        Call call = (Call) serv.createCall();
        call.removeProperty("xsd");
        call.setTargetEndpointAddress(url);
        // 添加相应的soap头信息
        //将字符串以流的形式存起来
        StringReader sr = new StringReader(System.getProperty("YGContract"));
        InputSource is = new InputSource(sr);
        //生成w3c element
        Element esb = (Element) DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(is).getElementsByTagName("Esb").item(0);
        //将element放在头中
        call.addHeader(new SOAPHeaderElement(esb));
//		HTTPHeaderHandler hhh = new HTTPHeaderHandler();
//		call.setClientHandlers(hhh, null);
//        call.setOperation("fpcy");
        call.setOperationName(new QName("http://www.ygsoft.com", "receiveData"));
//        call.addParameter("request", Constants.XSD_STRING, ParameterMode.IN);
        call.addParameter(new QName("http://www.ygsoft.com", "request"), Constants.XSD_STRING, ParameterMode.IN);
        String result = (String) call.invoke(new Object[]{body});
        logger.info("发送到远光合同==========================================\r\n" +
                "返回报文：" + result + "\r\n");
        return result;
    }
}

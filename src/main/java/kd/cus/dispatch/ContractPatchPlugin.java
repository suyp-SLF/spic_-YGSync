package kd.cus.dispatch;

import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.metadata.dynamicobject.DynamicObjectType;
import kd.bos.dataentity.utils.StringUtils;
import kd.bos.entity.MainEntityType;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.entity.property.LongProp;
import kd.bos.entity.property.PKFieldProp;
import kd.bos.entity.property.VarcharProp;
import kd.bos.exception.KDException;
import kd.bos.logging.Log;
import kd.bos.logging.LogFactory;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.service.business.datamodel.DynamicFormModelProxy;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.DBServiceHelper;
import kd.bos.servicehelper.MetadataServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.cus.api.PUBEntitys.FilterEntity;
import kd.cus.api.PUBThrowableUtils.ThrowableResultUtils;
import kd.cus.api.PUBSelectUtils.SelectGroupByPropertieDynamicObjectsUtils;
import kd.cus.api.util.XmlUtils;
import org.apache.axis.Constants;
import org.apache.axis.client.Call;
import org.apache.axis.client.Service;
import org.apache.axis.message.SOAPHeaderElement;
import org.dom4j.Document;
import org.dom4j.Element;
import org.xml.sax.InputSource;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.rpc.ParameterMode;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 合同轮询
 */
public class ContractPatchPlugin extends AbstractTask /* AbstractOperationServicePlugIn*/ {

    private static final String PURCONTRACT_LOGO = "conm_purcontract";//采购合同
    private static final String SALCONTRAL_LOGO = "conm_salcontract";//销售合同
    private static final String SPIC_CONM_TYPE_LOGO = "spic_conm_type";//二开合同类型
    private static final String BOS_USER_LOGO = "bos_user";//人员
    private static final String BOS_ORG_LOGO = "bos_org";//业务组织
    private static final String BOS_ADMINORG_LOGO = "bos_adminorg";//行政组织

    private static final String BD_SUPPLIER_LOGO = "bd_supplier";//供应商
    private static final String BD_BIZPARTNER_LOGO = "bd_bizpartner";//商务伙伴
    private static final String BD_CUSTOMER_LOGO = "bd_customer";
    private static final String BD_CURRENCY_LOGO = "bd_currency";//币别

    private static final String BOS_ASSISTANTDATA_DETAIL = "bos_assistantdata_detail";//辅助资料

    private static final String systemCode_value = "CWGX";
    private static final String securityCode_value = "CE24852C8D85E6E13E6F0E39B8AE1FF7";
    private static List<String> unitCodes_value = new ArrayList<>();
    private static String unitCode_value = "0";
    //    private static Integer currentPage_value = 1;
    private static final Integer pageSize_value = 100;
    private static String contractStatus = "APPROVED";
    private static Date startDate_value;
    private static Date endDate_value;

    private static Integer CURRENTPAGE_VALUE = 1;
    private static Integer TOTALPAGE_VALUE = 1;

    private static final SimpleDateFormat sdfTO = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

    private static Map<String, DynamicObject> list = new HashMap<>();
    private static Log logger = LogFactory.getLog(ContractPatchPlugin.class);

    private static Map<String, List<String>> purcontractResults = new HashMap<>();
    private static Map<String, List<String>> salcontractResults = new HashMap<>();
    private static List<String> postParamsList = new ArrayList<>();


    private String esbHeader = "<Esb>" +
            "<Route>" +
            "<Sender>cpi_jtcwgxpt</Sender>" +
            "<Time/>" +
            "<ServCode>cpi_fwxt.contractStandard.SynReq</ServCode>" +
            "<MsgId/>" +
            "<TransId/>" +
            "</Route>" +
            "</Esb>";

    private enum TYPE {
        AMOUNT, TIMER, BOOBLEN, DEFAULT;
    }


//    @Override
//    public void beforeExecuteOperationTransaction(BeforeOperationArgs e) {
//        try {
//            Map<String, Object> map = new HashMap<String, Object>(){{
//                put("startdate","2020-09-01");
//                put("enddate","2020-09-10");
//            }};
//            start(map);
//        } catch (Exception e1){
//            step0(e1);
//            throw new KDException(ThrowableResultUtils.getStackTrace(e1));
//        }
//    }

    @Override
    public void execute(RequestContext requestContext, Map<String, Object> map) throws KDException {
        try {
            start(map);
        } catch (Exception e1) {
            step0(e1, unitCode_value);
            throw new KDException(ThrowableResultUtils.getStackTrace(e1));
        }
    }

    private String start(Map<String, Object> map) throws Exception {
        step1("开始", unitCode_value);
        if (StringUtils.isNotBlank((String) map.get("startdate")) && StringUtils.isNotBlank((String) map.get("enddate"))) {
            //如果有时间启动参数将使用时间启动
            endDate_value = sdf.parse((String) map.get("enddate"));
            startDate_value = sdf.parse((String) map.get("startdate"));
        } else {
            //没有时间启动参数将使用当前日期为结束日期，开始日期为当前日期的前一天
            endDate_value = new Date();
            startDate_value = new Date(endDate_value.getTime() - 24 * 60 * 60 * 1000);
        }
        if (StringUtils.isNotBlank((String) map.get("unitCodes"))) {
            //有启动参数将启动参数添加
            unitCodes_value = Arrays.asList((String) map.get("unitCodes"));
        } else {
            //如果没有启动参数将使用所有的行政组织进行数据查询
            unitCodes_value = getAdminOrgList();
        }
        if (StringUtils.isNotBlank((String) map.get("contractStatus"))) {
            contractStatus = (String) map.get("contractStatus");
        }
        step5(StringUtils.join(unitCodes_value.toArray(), ","));
        NumberFormat numberFormat = NumberFormat.getInstance();
        numberFormat.setMaximumFractionDigits(0);
        int thisnumebr = 0;
        int allnumber = unitCodes_value.size();
        for (String unitCode : unitCodes_value) {
//            double process = (++thisnumebr/allnumber)*100;
//            feedbackProgress(Integer.parseInt(numberFormat.format(process)));
            unitCode_value = unitCode;
            //查询所需的行政组织进行法务轮询
            CURRENTPAGE_VALUE = 1;
            TOTALPAGE_VALUE = 1;
            while (CURRENTPAGE_VALUE <= TOTALPAGE_VALUE) {
                //判断当前页数是否为所有页数
                Object[] startObj = new Object[]{systemCode_value, securityCode_value, unitCode, CURRENTPAGE_VALUE.toString(), pageSize_value.toString(), contractStatus, sdf.format(startDate_value), sdf.format(endDate_value)};

                step2(CURRENTPAGE_VALUE.toString(), TOTALPAGE_VALUE.toString(), StringUtils.join(startObj, ","), unitCode_value);
                String response = "";
                try {
                    CURRENTPAGE_VALUE++;
                    response = post(startObj);
                    postParamsList.add(Arrays.stream(startObj).map(i -> i.toString()).collect(Collectors.joining(",", "{", "}")) + "->" + "发送并解析成功");
                    disposeXml(response);
                } catch (Exception e) {
                    postParamsList.add(Arrays.stream(startObj).map(i -> i.toString()).collect(Collectors.joining(",", "{", "}")) + "->" + "发送并解析失败:" + e.getMessage());
                } finally {

                }
            }
        }
        step7();
        step6();
        return null;
    }

    /**
     * 解析xml以便进行合同数据的保存
     *
     * @param xml
     * @throws Exception
     */
    private void disposeXml(String xml) throws Exception {
        if (StringUtils.isNotBlank(xml)) {
            Document document = XmlUtils.getDocument(xml);
            Element root = document.getRootElement();
//            CURRENTPAGE_VALUE = Integer.parseInt(root.elementText("CURRENTPAGE"));//当前页
            TOTALPAGE_VALUE = Integer.parseInt(root.elementText("TOTALPAGE"));//总页数
//            TOTALCOUNT_VALUE = root.elementText("TOTALCOUNT");//总条数
            List<Element> children = root.element("CONTRACTLIST").elements("CONTRACT");
            //当当前页大于总页数是将停止对当前时间端的数据的轮询
            save(children);
        }
    }

    /**
     * 保存一系列的合同数据
     *
     * @param children
     * @return
     * @throws Exception
     */
    private String save(List<Element> children) throws Exception {
        List<DynamicObject> salDataEntities = new ArrayList<>();
        List<DynamicObject> purDataEntities = new ArrayList<>();
        List<FilterEntity> filterList = new ArrayList<>();
        for (Element child : children) {


            String billname = child.elementText("CONTRACTNAME");
            String billno = child.elementText("CONTRACTCODE");
//            String spic_contracttype = child.elementText("CONTRACTTYPECODE");//合同类型
            String spic_contracttype = child.elementText("CONTRACTTYPENAME");//合同类型（名称）
            String spic_contractor = child.elementText("UNDERTAKEPERSONACCOUNT");//承办人编码
            String spic_contractdate = child.elementText("CREATIONTIME");//承办日期
            String spic_relatedproject = child.elementText("ISRELATEDPROJECT");//关联项目
            String PAY_TYPE = child.elementText("PAYMENTDIRECTION");//收支方向
            String spic_valuetype = child.elementText("VALUATIONMODE");//计价方式
            String spic_purchasetype = child.elementText("PURCHASETYPE");//采购方式
            String spic_contractobject = child.elementText("CONTRACTSUBJECT");//合同标的(名称)

            String this_code = child.elementText("SIGNINGSUBJECTNAME");//采购组织

            String settlecurrency = child.elementText("CURRENCYNAME");
            String totalallamount = child.elementText("CONTRACTAMOUNT");
            String totalamount = child.elementText("CONTRACT_AMOUNT_NOTAX");

            filterList.add(new FilterEntity(BOS_ORG_LOGO, "name", this_code));
            filterList.add(new FilterEntity(BOS_ASSISTANTDATA_DETAIL, "name", spic_contracttype));
            filterList.add(new FilterEntity(BOS_USER_LOGO, "number", spic_contractor));
            filterList.add(new FilterEntity(BOS_ASSISTANTDATA_DETAIL, "name", spic_valuetype));
            filterList.add(new FilterEntity(BOS_ASSISTANTDATA_DETAIL, "name", spic_purchasetype));
            filterList.add(new FilterEntity(BOS_ASSISTANTDATA_DETAIL, "name", spic_contractobject));

            List<Element> CONTRACTPLANS = child.element("CONTRACTPLANS").elements("CONTRACTPLAN");

            List<Element> opposites = child.element("OPPOSITES").elements("OPPOSITE");
            String chilsite_name = null;
            String chilsite_contactperson = null;
            String chilsite_paybank = null;
            String chilsite_paybankaccount = null;
            String chilsite_paybanknum = null;
            String chilsite_societycreditcode = null;
            DynamicObject this_dy;

            for (Element chilsite : opposites) {
                chilsite_name = chilsite.elementText("FULLNAME");
                chilsite_contactperson = chilsite.elementText("RELATIONNAME");
                chilsite_paybank = chilsite.elementText("BANKNAME");
                chilsite_paybankaccount = chilsite.elementText("BANKACCOUNT");
                chilsite_paybanknum = chilsite.elementText("BANKNUM");
                chilsite_societycreditcode = chilsite.elementText("CREDIRCODE");

                filterList.add(new FilterEntity(BOS_ASSISTANTDATA_DETAIL, "name", chilsite_name));
            }
        }

        list = SelectGroupByPropertieDynamicObjectsUtils.loadAll(list, filterList);
        Map<String, String> purResultMap = new HashMap<>();
        Map<String, String> salResultMap = new HashMap<>();
        int index = 0;
        for (Element child : children) {
            String billname = child.elementText("CONTRACTNAME");
            String billno = child.elementText("CONTRACTCODE");
//            String spic_contracttype = child.elementText("CONTRACTTYPECODE");//合同类型
            String spic_contracttype = child.elementText("CONTRACTTYPENAME");//合同类型（名称）
            String spic_contractor = child.elementText("UNDERTAKEPERSONACCOUNT");//承办人编码
            String spic_contractdate = child.elementText("CREATIONTIME");//承办日期
            String spic_relatedproject = child.elementText("ISRELATEDPROJECT");//关联项目
            String PAY_TYPE = child.elementText("PAYMENTDIRECTION");//收支方向
            String spic_valuetype = child.elementText("VALUATIONMODE");//计价方式
            String spic_purchasetype = child.elementText("PURCHASETYPE");//采购方式
            String spic_contractobject = child.elementText("CONTRACTSUBJECT");//合同标的(名称)

            String this_code = child.elementText("SIGNINGSUBJECTCODE");

            String settlecurrency = child.elementText("CURRENCYCODE");
            String totalallamount = child.elementText("CONTRACTAMOUNT");
            String totalamount = child.elementText("CONTRACT_AMOUNT_NOTAX");

            List<Element> CONTRACTPLANS = child.element("CONTRACTPLANS").elements("CONTRACTPLAN");

            List<Element> opposites = child.element("OPPOSITES").elements("OPPOSITE");
            String third_name = null;
            String third_contactperson = null;
            String third_paybank = null;
            String third_paybankaccount = null;
            String third_paybanknum = null;

            String opposite_name = null;
            String opposite_contactperson = null;
            String opposite_paybank = null;
            String opposite_paybankaccount = null;
            String opposite_paybanknum = null;
            String spic_societycreditcode = null;

            String party2nd = null;

            DynamicObject this_dy = null;
            Object pkValue;
            if ("付款".equals(PAY_TYPE)) {
                DynamicObjectType type = MetadataServiceHelper.getDataEntityType(PURCONTRACT_LOGO);
                Map<Class<?>, Object> services = new HashMap<>();
                DynamicFormModelProxy model = new DynamicFormModelProxy(PURCONTRACT_LOGO, UUID.randomUUID().toString(), services);
                model.createNewData();
                PKFieldProp pkProp = (PKFieldProp) type.getPrimaryKey();
                if (pkProp instanceof LongProp) {
                    model.getDataEntity().set(pkProp, DBServiceHelper.genGlobalLongId());
                } else if (pkProp instanceof VarcharProp) {
                    model.getDataEntity().set(pkProp, DBServiceHelper.genStringId());
                }
//                model.setValue("billname", contractname);
                this_dy = model.getDataEntity(true);
//                this_dy.set("billname", contractname);
//                model.updateCache();
                pkValue = model.getDataEntity().getPkValue();
//                Object e = pkProp.getValueFast(this_dy);
                opposite_name = child.elementText("SIGNINGSUBJECTNAME");
                this_dy.set("org", check(BOS_ORG_LOGO, "name", opposite_name));
                this_dy.set("party1st", opposite_name);
                if (opposites.size() > 0) {
                    Element opposite = opposites.get(0);
                    party2nd = opposite.elementText("FULLNAME");
                    this_dy.set("supplier", check(BD_SUPPLIER_LOGO, "name", party2nd));
                    this_dy.set("party2nd", party2nd);
                    this_dy.set("spic_societycreditcode", opposite.elementText("CREDIRCODE"));
//                String contactperson2nd = opposite.elementText("RELATIONNAME");
//                String spic_paybank2nd = opposite.elementText("BANKNAME");
//                String spic_paybankaccount2nd = opposite.elementText("BANKACCOUNT");
//                String spic_paybanknum2nd = opposite.elementText("BANKNUM");
//                String spic_societycreditcode2nd = opposite.elementText("CREDIRCODE");
                }
            } else if ("收款".equals(PAY_TYPE)) {
                DynamicObjectType type = MetadataServiceHelper.getDataEntityType(SALCONTRAL_LOGO);
                Map<Class<?>, Object> services = new HashMap<>();
                DynamicFormModelProxy model = new DynamicFormModelProxy(SALCONTRAL_LOGO, UUID.randomUUID().toString(), services);
                model.createNewData();
                PKFieldProp pkProp = (PKFieldProp) type.getPrimaryKey();
                if (pkProp instanceof LongProp) {
                    model.getDataEntity().set(pkProp, DBServiceHelper.genGlobalLongId());
                } else if (pkProp instanceof VarcharProp) {
                    model.getDataEntity().set(pkProp, DBServiceHelper.genStringId());
                }
//                model.setValue("billname", contractname);
                this_dy = model.getDataEntity(true);
//                this_dy.set("billname", contractname);
//                model.updateCache();
                pkValue = model.getDataEntity().getPkValue();
//                Object e = pkProp.getValueFast(this_dy);
                String org = child.elementText("SIGNINGSUBJECTNAME");
                this_dy.set("org", check(BOS_ORG_LOGO, "name", org));
                this_dy.set("party2nd", org);
                if (opposites.size() > 0) {
                    Element opposite = opposites.get(0);
                    opposite_name = opposite.elementText("FULLNAME");
                    this_dy.set("customer", check(BD_CUSTOMER_LOGO, "name", opposite_name));
                    this_dy.set("party1st", opposite_name);
                    this_dy.set("spic_societycreditcode", opposite.elementText("CREDIRCODE"));

//                Element opposite = opposites.get(0);
//                opposite_name = opposite.elementText("FULLNAME");
//                opposite_contactperson = opposite.elementText("RELATIONNAME");
//                opposite_paybank = opposite.elementText("BANKNAME");
//                opposite_paybankaccount = opposite.elementText("BANKACCOUNT");
//                opposite_paybanknum = opposite.elementText("BANKNUM");
//                spic_societycreditcode = opposite.elementText("CREDIRCODE");
                }
            } else {
                continue;
            }
            if (StringUtils.isBlank(billno) || "null".equalsIgnoreCase(billno)) {
                continue;
            }

            this_dy.set("validstatus", "B");

            this_dy.set("billname", billname);
            this_dy.set("billno", billno);//
            this_dy.set("spic_contracttype", check(BOS_ASSISTANTDATA_DETAIL, "name", spic_contracttype));
            this_dy.set("spic_contractor", check(BOS_USER_LOGO, spic_contractor));
            this_dy.set("spic_contractdate", check(TYPE.TIMER, spic_contractdate));
            this_dy.set("spic_relatedproject", check(TYPE.BOOBLEN, spic_relatedproject));
            this_dy.set("spic_valuetype", check(BOS_ASSISTANTDATA_DETAIL, "name", spic_valuetype));
            this_dy.set("spic_purchasetype", check(BOS_ASSISTANTDATA_DETAIL, "name", spic_purchasetype));
            this_dy.set("spic_contractobject", check(BOS_ASSISTANTDATA_DETAIL, "name", spic_contractobject));

//            this_dy.set("org", check(BOS_ORG_LOGO, this_code));
//            this_dy.set("contactperson1st", check(TYPE.DEFAULT, contactperson1st));
//            this_dy.set("spic_paybank1st", check(TYPE.DEFAULT, spic_paybank1st));
//            this_dy.set("spic_paybankaccount1st", check(TYPE.DEFAULT, spic_paybankaccount1st));
//            this_dy.set("spic_paybanknum1st", check(TYPE.DEFAULT, spic_paybanknum1st));
//            this_dy.set("spic_paybankname1st", check(TYPE.DEFAULT, spic_paybankname1st));
//            this_dy.set("phone1st", check(TYPE.DEFAULT, phone1st));

//            this_dy.set("supplier", check(BD_SUPPLIER_LOGO, supplier));
//            this_dy.set("party2nd", check(TYPE.DEFAULT, opposite_name));
            this_dy.set("contactperson2nd", check(TYPE.DEFAULT, opposite_contactperson));
//                this_dy.set("spic_paybank2nd", check(TYPE.DEFAULT, ));
            this_dy.set("spic_paybankaccount2nd", check(TYPE.DEFAULT, opposite_paybankaccount));
            this_dy.set("spic_paybanknum2nd", check(TYPE.DEFAULT, opposite_paybanknum));
            this_dy.set("spic_paybankname2nd", check(TYPE.DEFAULT, opposite_paybank));
//                this_dy.set("phone2nd", check(TYPE.DEFAULT, phone2nd));

            this_dy.set("partc", check(BD_BIZPARTNER_LOGO, third_name));
            this_dy.set("spic_contactperson3rd", check(TYPE.DEFAULT, third_contactperson));
//            this_dy.set("spic_paybank3rd", check(TYPE.DEFAULT, third_paybank));
            this_dy.set("spic_paybankaccount3rd", check(TYPE.DEFAULT, third_paybankaccount));
            this_dy.set("spic_paybanknum3rd", check(TYPE.DEFAULT, third_paybanknum));
            this_dy.set("spic_paybankname3rd", check(TYPE.DEFAULT, third_paybank));
//                this_dy.set("spic_phone3rd", check(TYPE.DEFAULT, spic_phone3rd));

            this_dy.set("settlecurrency", check(BD_CURRENCY_LOGO, settlecurrency));
            this_dy.set("totalallamount", check(TYPE.AMOUNT, totalallamount));
            this_dy.set("totalamount", check(TYPE.AMOUNT, totalamount));

            String childLog = "" +
                    ++index + " :--" + unitCode_value + "ContractLOG:list----" + PAY_TYPE + "---------------------------------------------------------------------------------------------------------------------------\r\n" +
                    "org(组织名称)" + ":" + opposite_name + "基础资料:" + (null != this_dy.getDynamicObject("org")) + "\r\n" +
                    "spic_societycreditcode(统一社会信用)" + ":" + spic_societycreditcode + "\r\n";
            if ("付款".equals(PAY_TYPE)) {
                childLog += "supplier(供应商)" + ":" + party2nd + "基础资料：" + (null != this_dy.getDynamicObject("supplier")) + "\r\n";
            } else if ("收款".equals(PAY_TYPE)) {
                childLog += "";
            }
            childLog += "billname(合同名称)" + ":" + billname + "\r\n" +
                    "billno(合同编号)" + ":" + billno + "\r\n" +
                    "spic_contracttype(合同分类)" + ":" + spic_contracttype + "基础资料:" + (null != this_dy.getDynamicObject("spic_contracttype")) + "\r\n" +
                    "spic_contractor(承办人)" + ":" + spic_contractor + "基础资料:" + (null != this_dy.getDynamicObject("spic_contractor")) + "\r\n" +
                    "spic_contractdate(承办日期)" + ":" + spic_contractdate + "\r\n" +
                    "spic_relatedproject(关联项目)" + ":" + spic_relatedproject + "\r\n" +
                    "spic_valuetype(计价方式)" + ":" + spic_valuetype + "基础资料:" + (null != this_dy.getDynamicObject("spic_valuetype")) + "\r\n" +
                    "spic_purchasetype(采购方式)" + ":" + spic_purchasetype + "基础资料:" + (null != this_dy.getDynamicObject("spic_purchasetype")) + "\r\n" +
                    "spic_contractobject(合同标的)" + ":" + spic_contractobject + "基础资料:" + (null != this_dy.getDynamicObject("spic_contractobject")) + "\r\n" +
                    "party2nd(对方)" + ":" + opposite_name + "\r\n" +
                    "contactperson2nd(对方联系人)" + ":" + opposite_contactperson + "\r\n" +
                    "spic_paybankaccount2nd(对方银行账户)" + ":" + opposite_paybankaccount + "\r\n" +
                    "spic_paybanknum2nd(对方银行账号)" + ":" + opposite_paybanknum + "\r\n" +
                    "spic_paybankname2nd(对方银行行号)" + ":" + opposite_paybank + "\r\n" +
                    "partc(第三方)" + ":" + third_name + "\r\n" +
                    "spic_contactperson3rd(第三方联系人)" + ":" + third_contactperson + "\r\n" +
                    "spic_paybankaccount3rd(第三方银行账户)" + ":" + third_paybankaccount + "\r\n" +
                    "spic_paybankname3rd(第三方银行行号)" + ":" + third_paybank + "\r\n" +
                    "settlecurrency(结算币别)" + ":" + settlecurrency + "基础资料:" + (null != this_dy.getDynamicObject("settlecurrency")) + "\r\n" +
                    "totalallamount(合同金额)" + ":" + totalallamount + "\r\n" +
                    "totalamount(合同金额（不含税）)" + ":" + totalamount + "\r\n" +
                    "salDataEntities:" + salDataEntities.size() + "\r\n" +
                    "purDataEntities:" + purDataEntities.size() + "\r\n";

            logger.info(childLog);

            DynamicObjectCollection payentry_COL = this_dy.getDynamicObjectCollection("payentry");
            payentry_COL.clear();
            DynamicObjectType payentry_YTPE = payentry_COL.getDynamicObjectType();
            for (Element CONTRACTPLAN : CONTRACTPLANS) {
                DynamicObject payentry_ONE = new DynamicObject(payentry_YTPE);
                String paydate = CONTRACTPLAN.elementText("DUEDATE"),//付款日期---PAYDATE
                        payamount = CONTRACTPLAN.elementText("PLANAMOUNT"),//付款金额---PAYAMOUNT
                        payrate = CONTRACTPLAN.elementText("RECEIPTPAYPLANPERCENT");//付款比例---PAYRATE
                payentry_ONE.set("paydate", check(TYPE.TIMER, paydate));
                payentry_ONE.set("payamount", check(TYPE.AMOUNT, payamount));
                payentry_ONE.set("payrate", check(TYPE.DEFAULT, payrate));
                payentry_COL.add(payentry_ONE);
            }
            if ("付款".equals(PAY_TYPE)) {
                purDataEntities.add(this_dy);
                purResultMap.put(pkValue.toString(), billname + "::" + PAY_TYPE + "-->");
            } else if ("收款".equals(PAY_TYPE)) {
                salDataEntities.add(this_dy);
                salResultMap.put(pkValue.toString(), billname + "::" + PAY_TYPE + "-->");
            }
        }
        //保存采购
        OperationResult operationResultSal = OperationServiceHelper.executeOperate("save", SALCONTRAL_LOGO, salDataEntities.toArray(new DynamicObject[salDataEntities.size()]), OperateOption.create());
        //保存销售
        OperationResult operationResultPur = OperationServiceHelper.executeOperate("save", PURCONTRACT_LOGO, purDataEntities.toArray(new DynamicObject[purDataEntities.size()]), OperateOption.create());
        //查看采购合同的错误
        operationResultSal.getAllErrorOrValidateInfo().stream().forEach(iop -> {
            salResultMap.put(iop.getPkValue().toString(), salResultMap.get(iop.getPkValue().toString()) + "|" + iop.getMessage());
        });
        //查看销售合同的错误
        operationResultPur.getAllErrorOrValidateInfo().stream().forEach(iop -> {
            purResultMap.put(iop.getPkValue().toString(), purResultMap.get(iop.getPkValue().toString()) + "|" + iop.getMessage());
        });
        StringBuffer resultStr = new StringBuffer();
        List<String> _orgPurContractResult = new ArrayList<>();
        List<String> _orgSalContractResult = new ArrayList<>();
        purResultMap.entrySet().forEach(res -> {
            resultStr.append(res.getValue() + "\r\n");
            _orgPurContractResult.add(res.getValue());
        });
        salResultMap.entrySet().forEach(res -> {
            resultStr.append(res.getValue() + "\r\n");
            _orgSalContractResult.add(res.getValue());
        });
        purcontractResults.put(unitCode_value, _orgPurContractResult);
        salcontractResults.put(unitCode_value, _orgSalContractResult);
        step4(resultStr.toString(), unitCode_value);
        return null;
    }

    /**
     * http post方法
     *
     * @param body
     * @return
     * @throws IOException
     */
    private String post(Object[] body) throws Exception {
        String url = System.getProperty("esbUrl");
        Service serv = new Service();
        Call call = (Call) serv.createCall();
        call.removeProperty("xsd");
        call.setTargetEndpointAddress(url);
        // 添加相应的soap头信息
        //将字符串以流的形式存起来
        StringReader sr = new StringReader(System.getProperty("FWContract"));
        InputSource is = new InputSource(sr);
        //生成w3c element
        org.w3c.dom.Element esb = (org.w3c.dom.Element) DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(is).getElementsByTagName("Esb").item(0);
        //将element放在头中
        call.addHeader(new SOAPHeaderElement(esb));
//		HTTPHeaderHandler hhh = new HTTPHeaderHandler();
//		call.setClientHandlers(hhh, null);
//        call.setOperation("fpcy");
        call.setOperationName(new QName("http://contractStandardInterface.ws.spicProject.rcugrc.hd.com/", "getContractInfoList"));
//        call.addParameter("request", Constants.XSD_STRING, ParameterMode.IN);
        call.addParameter(new QName("systemCode"), Constants.XSD_STRING, ParameterMode.IN);
        call.addParameter(new QName("securityCode"), Constants.XSD_STRING, ParameterMode.IN);
        call.addParameter(new QName("unitCodes"), Constants.XSD_STRING, ParameterMode.IN);
        call.addParameter(new QName("currentPage"), Constants.XSD_STRING, ParameterMode.IN);
        call.addParameter(new QName("pageSize"), Constants.XSD_STRING, ParameterMode.IN);
        call.addParameter(new QName("contractStatus"), Constants.XSD_STRING, ParameterMode.IN);
        call.addParameter(new QName("startDate"), Constants.XSD_STRING, ParameterMode.IN);
        call.addParameter(new QName("endDate"), Constants.XSD_STRING, ParameterMode.IN);
        String result = (String) call.invoke(body);
        step3(result, unitCode_value);
        return result;
    }

    /**
     * 校验基础资料
     *
     * @param entityNumber
     * @param properite
     * @param value
     * @return
     */
    private Object check(String entityNumber, String properite, String value) {
        return list.get(entityNumber + "@_@" + properite + "@_@" + value);
    }

    private Object check(String entityNumber, String value) {
        return check(entityNumber, "number", value);
    }


    /**
     * 校验转换参数
     *
     * @param type
     * @param value
     * @return
     * @throws ParseException
     */
    private Object check(TYPE type, String value) throws Exception {
        if (StringUtils.isBlank(value)) {
            value = "";
        }
        switch (type) {
            case DEFAULT:
                return value;
            case TIMER:
                if (StringUtils.isNotBlank(value)) {
                    return value.contains("T") ? sdfTO.parse(value) : sdf.parse(value);
                }
                return null;
            case BOOBLEN:
                return "1".equals(value) ? true : false;
            case AMOUNT:
                if (StringUtils.isNotBlank(value)) {
                    BigDecimal bigDecimal = new BigDecimal(value);
                    return bigDecimal.multiply(new BigDecimal(10000));
                }
                return null;
        }
        return null;
    }

    /**
     * 日志
     */
    private static void step0(Throwable e, String org) {
        String step0 = "" +
                "step:0----" + org + "ContractLOG--------------------------------------------------------------------------------------------\r\n" +
                "| " + "调用轮询程序出错" + "\r\n" +
                "| e：" + "\r\n" +
                getStackTrace(e) +
                "step:0----ContractLOG--------------------------------------------------------------------------------------------\r\n";
        logger.info(step0);
    }

    private static void step1(String str, String org) {
        String step1 = "" +
                "step:1----" + org + "ContractLOG--------------------------------------------------------------------------------------------\r\n" +
                "|" + "调用法务轮询合同" + "\r\n" +
                "|" + str + "\r\n" +
                "step:1----ContractLOG--------------------------------------------------------------------------------------------\r\n";
        logger.info(step1);
    }

    private static void step2(String page, String pageAll, String request, String org) {
        String step2 = "" +
                "step:2----" + org + "ContractLOG--------------------------------------------------------------------------------------------\r\n" +
                "| 组建请求参数" + "\r\n" +
                "| page:" + page + "\\" + pageAll + "\r\n" +
                "| request:" + "\r\n" +
                "| " + request + "\r\n" +
                "step:2----ContractLOG--------------------------------------------------------------------------------------------\r\n";
        logger.info(step2);
    }

    private static void step3(String response, String org) {
        String step3 = "" +
                "step:3----" + org + "ContractLOG--------------------------------------------------------------------------------------------\r\n" +
                "| 获得相应报文" + "\r\n" +
                "| response:" + "\r\n" +
                "| " + response + "\r\n" +
                "step:3----ContractLOG--------------------------------------------------------------------------------------------\r\n";
        logger.info(step3);
    }

    private static void step4(String result, String org) {
        String step4 = "" +
                "step:4----" + org + "ContractLOG--------------------------------------------------------------------------------------------\r\n" +
                "| 保存情况" + "\r\n" +
                "| result:" + "\r\n" +
                "| " + result + "\r\n" +
                "step:4----ContractLOG--------------------------------------------------------------------------------------------\r\n";
        logger.info(step4);
    }

    private static void step5(String orgs) {
        String step5 = "" +
                "step:5----ContractLOG:ORGS--------------------------------------------------------------------------------------------\r\n" +
                "| 行政组织情况" + "\r\n" +
                "| result:" + "\r\n" +
                "| " + orgs + "\r\n" +
                "step:5----ContractLOG--------------------------------------------------------------------------------------------\r\n";
        logger.info(step5);
    }

    private static void step6() {

        purcontractResults.entrySet().forEach(item -> {
            int index = 0;
            StringBuffer step6 = new StringBuffer();
            String mark = item.getKey();
            step6.append("step:6----ContractLOG:RESULT---" + mark + "-----------------------------------------------------------------------------------------\r\n");
            item.getValue().forEach(it -> {
                step6.append(it + "\r\n");
            });
            step6.append("step:6----ContractLOG---" + mark + "-----------------------------------------------------------------------------------------\r\n");
            logger.info(step6.toString());
        });
        salcontractResults.entrySet().forEach(item -> {
            int index = 0;
            StringBuffer step6 = new StringBuffer();
            String mark = item.getKey();
            step6.append("step:6----ContractLOG:RESULT---" + mark + "-----------------------------------------------------------------------------------------\r\n");
            item.getValue().forEach(it -> {
                step6.append(it + "\r\n");
            });
            step6.append("step:6----ContractLOG---" + mark + "-----------------------------------------------------------------------------------------\r\n");
            logger.info(step6.toString());
        });
    }

    private static void step7() {
        int index = 0;
        StringBuffer step7 = new StringBuffer();
        step7.append("step:7----ContractLOG:RESULT--------------------------------------------------------------------------------------------\r\n");
        postParamsList.forEach(it -> {
            step7.append(it + "\r\n");
        });
        step7.append("step:7----ContractLOG:RESULT--------------------------------------------------------------------------------------------\r\n");
        logger.info(step7.toString());
    }

    //将报错的方法的所有报错转成string
    private static String getStackTrace(Throwable throwable) {
        if (null != throwable) {
            StringWriter stringWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(stringWriter);
            try {
                throwable.printStackTrace(printWriter);
                return throwable.getMessage() + stringWriter.toString();
            } finally {
                printWriter.close();
            }
        } else {
            return "";
        }
    }

    /**
     * 获得所有的行政组织
     *
     * @return
     */
    private List<String> getAdminOrgList() {
        MainEntityType orgType = MetadataServiceHelper.getDataEntityType(BOS_ADMINORG_LOGO);
        QFilter[] qFilters = new QFilter[]{QFilter.isNotNull("number")};
        Map<Object, DynamicObject> org_dys = BusinessDataServiceHelper.loadFromCache(orgType, qFilters);
        return org_dys.entrySet().stream().map(i -> i.getValue()).map(i -> i.get("number").toString()).collect(Collectors.toList());
    }
}

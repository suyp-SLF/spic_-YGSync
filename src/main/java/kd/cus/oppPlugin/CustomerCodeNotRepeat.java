package kd.cus.oppPlugin;

import java.text.SimpleDateFormat;
import java.util.ArrayList;

import org.apache.commons.lang.StringUtils;
import org.tmatesoft.sqljet.core.internal.lang.SqlParser.result_column_return;

import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.ExtendedDataEntity;
import kd.bos.entity.plugin.AbstractOperationServicePlugIn;
import kd.bos.entity.plugin.AddValidatorsEventArgs;
import kd.bos.entity.plugin.PreparePropertysEventArgs;
import kd.bos.entity.plugin.args.AfterOperationArgs;
import kd.bos.entity.plugin.args.BeforeOperationArgs;
import kd.bos.entity.plugin.args.BeginOperationTransactionArgs;
import kd.bos.entity.validate.AbstractValidator;
import kd.bos.entity.validate.ErrorLevel;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.message.MessageServiceHelper;

//去重
public class CustomerCodeNotRepeat extends AbstractOperationServicePlugIn {

	@Override
	public void onAddValidators(AddValidatorsEventArgs e) {
		super.onAddValidators(e);
		WriteMeetingOpinionValidator opinionVal = new WriteMeetingOpinionValidator();
		e.addValidator(opinionVal);
	}

	public class WriteMeetingOpinionValidator extends AbstractValidator {

		@Override
		public void validate() {
			// 获取校验的数据数组
			ExtendedDataEntity[] data = this.getDataEntities();
			// 获取唯一识别码
			for (ExtendedDataEntity extendedDataEntity : data) {
				// 获取统一社会信用代码
				Object societycreditcode = extendedDataEntity.getValue("societycreditcode");
				Object pkid = extendedDataEntity.getBillPkId();
				// 查重统一社会信用代码
				try {
					if (StringUtils.isNotEmpty(societycreditcode.toString())) {
						getBill(societycreditcode, pkid);
					}
				} catch (Exception ex) {
					this.addErrorMessage(extendedDataEntity, ex.getMessage());
				}
			}
		}
	}

	private void getBill(Object societycreditcode, Object pkid) throws Exception {
		QFilter[] filters = { new QFilter("societycreditcode", QCP.equals, societycreditcode) };
		DynamicObject[] dy = BusinessDataServiceHelper.load("bd_bizpartner", "id", filters);

		for (DynamicObject dynamicObject : dy) {
			QFilter[] filters1 = { new QFilter("bizpartner", QCP.equals, dynamicObject.getPkValue()),
					new QFilter("id", QCP.not_equals, pkid) };
			DynamicObject result = BusinessDataServiceHelper.loadSingle("bd_customer", "id,bizpartner", filters1);
			if (result != null) {
				throw new Exception("统一社会信用代码重复，重复项的编码：" + result.getString("number"));
			}
		}
	}
}

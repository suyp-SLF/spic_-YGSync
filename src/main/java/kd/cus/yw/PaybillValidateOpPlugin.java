package kd.cus.yw;

import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.ExtendedDataEntity;
import kd.bos.entity.plugin.AbstractOperationServicePlugIn;
import kd.bos.entity.plugin.AddValidatorsEventArgs;
import kd.bos.entity.plugin.PreparePropertysEventArgs;
import kd.bos.entity.plugin.args.BeforeOperationArgs;
import kd.bos.entity.validate.AbstractValidator;

/**
 * @author dhf
 * 付款单提交校验
 */
public class PaybillValidateOpPlugin extends AbstractOperationServicePlugIn {
	
	@Override
	public void onPreparePropertys(PreparePropertysEventArgs e) {
		super.onPreparePropertys(e);
		e.getFieldKeys().add("settletype");
		e.getFieldKeys().add("settletnumber");
		e.getFieldKeys().add("entry");
		e.getFieldKeys().add("entry.e_fundflowitem");
		e.getFieldKeys().add("spic_usageother");
	}
	
	@Override
	public void beforeExecuteOperationTransaction(BeforeOperationArgs e) {
		super.beforeExecuteOperationTransaction(e);
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
				DynamicObject settletype = dataEntity.getDynamicObject("settletype");//结算方式
				String settletnumber = dataEntity.getString("settletnumber");//结算号
				DynamicObjectCollection entry = dataEntity.getDynamicObjectCollection("entry");
				
				if (settletype != null) {
					String settlementtype = settletype.getString("settlementtype");//类别				
					//结算方式是现金
					if ("0".equals(settlementtype)) {
						
					} else if ("3".equals(settlementtype)) {//结算方式是汇兑
						
					} else if ("8".equals(settlementtype)) {//结算方式是电子结算
						
					} else {//结算方式是支票、汇票、信用证
						//逻辑或有短路现象
						if (settletnumber == null || "".equals(settletnumber)) {//结算号文本为空
							this.addErrorMessage(extendedDataEntity, "未填写结算号，不能提交");
							continue;
						}
					}
				} else {
					this.addErrorMessage(extendedDataEntity, "未选择结算方式，不能提交");
					continue;
				}
				
				int rowCount = entry.getRowCount();
				if (rowCount > 0) {//有分录行
					DynamicObject firstEntryRow = entry.get(0);
					if (firstEntryRow != null) {
						DynamicObject fundflowitem = firstEntryRow.getDynamicObject("e_fundflowitem");//资金用途
						if (fundflowitem != null) {
							String fundflowtype = fundflowitem.getString("spic_fundflowtype");//资金用途分类
//							//资金用途分类是其他
							if ("99".equals(fundflowtype)) {
								String usageother = firstEntryRow.getString("spic_usageother");//获取补充说明
								//补充说明为空
								if (usageother == null || "".equals(usageother)) {
									this.addErrorMessage(extendedDataEntity, "未填写补充说明，不能提交");
									continue;
								}
							}
						} 
					}
				} 
			}
		}
	}
}

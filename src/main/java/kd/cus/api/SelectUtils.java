package kd.cus.api;

import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.utils.StringUtils;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.cus.api.entity.FilterEntity;

import java.util.*;
import java.util.stream.Collectors;

public class SelectUtils {

    public static String fetchGroupKey(FilterEntity filterEntity){
        return filterEntity.getEntityNumber() + "@_@" + filterEntity.getPropertie()  + "@_@" + filterEntity.getOrg()+ "@_@" + filterEntity.getGetPropertie();
    }

    /**
     *
     * @return
     */
    public static Map<String, DynamicObject> loadAll(Map<String, DynamicObject> list, List<FilterEntity> filterList){
        if (null == list){
            list = new HashMap<>();
        }
        Map<String,List<FilterEntity>> cateMap = filterList
                .stream()
                .collect(Collectors.groupingBy(e ->fetchGroupKey(e)));
        Iterator<Map.Entry<String, List<FilterEntity>>> cateMapIt = cateMap.entrySet().iterator();
        while (cateMapIt.hasNext()){
            Map.Entry<String, List<FilterEntity>> item = cateMapIt.next();
            List<String> values = new ArrayList<>();
            item.getValue().forEach(filterEntity -> {
                values.add(filterEntity.getValue());
            });
            String entityNumber = item.getKey().split("@_@")[0];
            String propertie = item.getKey().split("@_@")[1];
            String org = item.getKey().split("@_@")[2];
            QFilter qFilter = new QFilter(propertie,QCP.in, values);
            if (!"null".equals(org) && StringUtils.isNotBlank(org)){
                qFilter.and(new QFilter("org",QCP.equals,org));
            }
            DynamicObject[] dys = BusinessDataServiceHelper.load(entityNumber, propertie, new QFilter[]{qFilter});
            for(DynamicObject dy : dys){
                list.put(entityNumber+ "@_@" +propertie + "@_@" + dy.getString(propertie),dy);
            }
        }
        return list;
    }
    
    public static Map<String, DynamicObject> loadAllPRO(Map<String, DynamicObject> list, List<FilterEntity> filterList){
        if (null == list){
            list = new HashMap<>();
        }
        Map<String,List<FilterEntity>> cateMap = filterList
                .stream()
                .collect(Collectors.groupingBy(e ->fetchGroupKey(e)));
        Iterator<Map.Entry<String, List<FilterEntity>>> cateMapIt = cateMap.entrySet().iterator();
        while (cateMapIt.hasNext()){
            Map.Entry<String, List<FilterEntity>> item = cateMapIt.next();
            List<String> values = new ArrayList<>();
            item.getValue().forEach(filterEntity -> {
                values.add(filterEntity.getValue());
            });
            String entityNumber = item.getKey().split("@_@")[0];
            String propertie = item.getKey().split("@_@")[1];
            String getPropertie = item.getKey().split("@_@")[3];
            String org = item.getKey().split("@_@")[2];
            QFilter qFilter = new QFilter(propertie,QCP.in, values);
            if (!"null".equals(org) && StringUtils.isNotBlank(org)){
                qFilter.and(new QFilter("org",QCP.equals,org));
            }
            DynamicObject[] dys = BusinessDataServiceHelper.load(entityNumber, getPropertie, new QFilter[]{qFilter});
            for(DynamicObject dy : dys){
                list.put(entityNumber+ "@_@" +propertie + "@_@" + dy.getString(propertie),dy);
            }
        }
        return list;
    }
}

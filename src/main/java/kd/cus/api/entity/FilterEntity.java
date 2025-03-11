package kd.cus.api.entity;

public class FilterEntity {

    private String entityNumber;
    private String propertie;
    private String getPropertie;
    private String value;
    private String org;

    @Override
    public int hashCode() {
        return 1;
    }

    @Override
    public boolean equals(Object obj) {
        FilterEntity filterEntity = (FilterEntity)obj;
        return filterEntity.entityNumber.equals(this.entityNumber)&&filterEntity.propertie.equals(this.propertie)&&filterEntity.getPropertie.equals(this.getPropertie);
    }

    public FilterEntity(String entityNumber, String propertie, String value){
        this.entityNumber = entityNumber;
        this.propertie = propertie;
        this.value = value;
    }
    public FilterEntity(String entityNumber, String propertie, String value, String org){
        this.entityNumber = entityNumber;
        this.propertie = propertie;
        this.value = value;
        this.org = org;
    }
    public FilterEntity(String entityNumber, String propertie, String value, String org, String getPropertie){
        this.entityNumber = entityNumber;
        this.propertie = propertie;
        this.getPropertie = getPropertie;
        this.value = value;
        this.org = org;
    }

    public String getEntityNumber() {
        return entityNumber;
    }

    public void setEntityNumber(String entityNumber) {
        this.entityNumber = entityNumber;
    }

    public String getPropertie() {
        return propertie;
    }

    public void setPropertie(String propertie) {
        this.propertie = propertie;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getOrg() {
        return org;
    }

    public void setOrg(String org) {
        this.org = org;
    }

	public String getGetPropertie() {
		return getPropertie;
	}

	public void setGetPropertie(String getPropertie) {
		this.getPropertie = getPropertie;
	}
    
    
}

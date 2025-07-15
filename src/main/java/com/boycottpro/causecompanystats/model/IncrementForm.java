package com.boycottpro.causecompanystats.model;

public class IncrementForm {
    private String company_name;
    private String cause_desc;
    private boolean increment;

    public IncrementForm() {}

    public IncrementForm(String company_name, String cause_desc, boolean increment) {
        this.company_name = company_name;
        this.cause_desc = cause_desc;
        this.increment = increment;
    }

    public String getCompany_name() { return company_name; }
    public void setCompany_name(String company_name) { this.company_name = company_name; }

    public String getCause_desc() { return cause_desc; }
    public void setCause_desc(String cause_desc) { this.cause_desc = cause_desc; }

    public boolean isIncrement() { return increment; }
    public void setIncrement(boolean increment) { this.increment = increment; }
}

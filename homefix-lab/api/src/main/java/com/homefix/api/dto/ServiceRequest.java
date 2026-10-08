package com.homefix.api.dto;

public class ServiceRequest {
    private String customerName;
    private String service;
    private String address;
    private String preferredDate;
    private String notes;

    public ServiceRequest() {
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPreferredDate() {
        return preferredDate;
    }    
    
    
  public void setPreferredDate(String preferredDate) {
        this.preferredDate = preferredDate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}

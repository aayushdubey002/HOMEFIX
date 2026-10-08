package com.homefix.api.dto;

public class Booking {
    private String id;
    private String customerName;
    private String service;
    private String providerName;
    private String status;

    public Booking() {
    }

    public Booking(String id, String customerName, String service, String providerName, String status) {
        this.id = id;
        this.customerName = customerName;
        this.service = service;
        this.providerName = providerName;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
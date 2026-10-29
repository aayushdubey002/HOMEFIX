package com.homefix.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ServiceRequest {

    @NotBlank
    @Size(max = 120)
    private String customerName;

    @NotBlank
    @Size(max = 80)
    private String service;

    @NotBlank
    @Pattern(regexp = "[0-9]{10}")
    private String phone;

    @NotBlank
    @Size(max = 500)
    private String address;

    @NotBlank
    private String preferredDate;

    @Pattern(regexp = "([01][0-9]|2[0-3]):[0-5][0-9]")
    private String preferredTime;
    private Double latitude;
    private Double longitude;
    private String city;
    @Size(max = 2000)
    private String notes;

    @Size(max = 4194304, message = "Problem photo must be 3 MB or smaller.")
    private String problemImageBase64;

    @Pattern(regexp = "CASH|UPI", flags = Pattern.Flag.CASE_INSENSITIVE)
    private String paymentMethod;

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

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
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

    public String getPreferredTime() {
        return preferredTime;
    }

    public void setPreferredTime(String preferredTime) {
        this.preferredTime = preferredTime;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getProblemImageBase64() {
        return problemImageBase64;
    }

    public void setProblemImageBase64(String problemImageBase64) {
        this.problemImageBase64 = problemImageBase64;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }
}
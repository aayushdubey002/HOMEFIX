package com.homefix.api.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "services")
public class HomeService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String category;
    private double price;
    private String location;
    private String availability;
    private double rating;
    private String provider;
    private String status;

    private String serviceName;
    private String providerName;
    private String phone;
    private String serviceArea;

    public HomeService() {
    }

    public HomeService(String name, String category, double price, String location,
                       String availability, double rating, String provider, String status) {
        this.name = name;
        this.category = category;
        this.price = price;
        this.location = location;
        this.availability = availability;
        this.rating = rating;
        this.provider = provider;
        this.status = status;
        this.serviceName = name;
        this.providerName = provider;
        this.serviceArea = location;
    }

    public HomeService(String serviceName, String providerName, String phone, String serviceArea) {
        this.serviceName = serviceName;
        this.providerName = providerName;
        this.phone = phone;
        this.serviceArea = serviceArea;
        this.name = serviceName;
        this.provider = providerName;
        this.location = serviceArea;
        this.status = "active";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() {
        return name != null ? name : serviceName;
    }
    public void setName(String name) {
        this.name = name;
        if (this.serviceName == null) {
            this.serviceName = name;
        }
    }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public String getLocation() {
        return location != null ? location : serviceArea;
    }
    public void setLocation(String location) {
        this.location = location;
        if (this.serviceArea == null) {
            this.serviceArea = location;
        }
    }

    public String getAvailability() { return availability; }
    public void setAvailability(String availability) { this.availability = availability; }

    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }

    public String getProvider() {
        return provider != null ? provider : providerName;
    }
    public void setProvider(String provider) {
        this.provider = provider;
        if (this.providerName == null) {
            this.providerName = provider;
        }
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getServiceName() {
        return serviceName != null ? serviceName : name;
    }
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
        if (this.name == null) {
            this.name = serviceName;
        }
    }

    public String getProviderName() {
        return providerName != null ? providerName : provider;
    }
    public void setProviderName(String providerName) {
        this.providerName = providerName;
        if (this.provider == null) {
            this.provider = providerName;
        }
    }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getServiceArea() {
        return serviceArea != null ? serviceArea : location;
    }
    public void setServiceArea(String serviceArea) {
        this.serviceArea = serviceArea;
        if (this.location == null) {
            this.location = serviceArea;
        }
    }
}
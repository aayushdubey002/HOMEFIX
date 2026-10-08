package com.homefix.api.dto;

public class DashboardStats {
    private int totalCustomers;
    private int activeProviders;
    private int totalBookings;
    private int pendingRequests;

    public DashboardStats() {
    }

    public DashboardStats(int totalCustomers, int activeProviders, int totalBookings, int pendingRequests) {
        this.totalCustomers = totalCustomers;
        this.activeProviders = activeProviders;
        this.totalBookings = totalBookings;
        this.pendingRequests = pendingRequests;
    }

    public int getTotalCustomers() {
        return totalCustomers;
    }

    public void setTotalCustomers(int totalCustomers) {
        this.totalCustomers = totalCustomers;
    }

    public int getActiveProviders() {
        return activeProviders;
    }

    public void setActiveProviders(int activeProviders) {
        this.activeProviders = activeProviders;
    }

    public int getTotalBookings() {
        return totalBookings;
    }

    public void setTotalBookings(int totalBookings) {
        this.totalBookings = totalBookings;
    }

    public int getPendingRequests() {
        return pendingRequests;
    }

    public void setPendingRequests(int pendingRequests) {
        this.pendingRequests = pendingRequests;
    }
}

package com.homefix.api.config;

import com.homefix.api.model.HomeService;
import com.homefix.api.repository.HomeServiceRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    private final HomeServiceRepository repo;

    public DataSeeder(HomeServiceRepository repo) {
        this.repo = repo;
    }

    @Override
    public void run(String... args) {
        if (repo.count() == 0) {
            repo.save(new HomeService("AC Repair", "Appliance", 1500, "Mumbai", "Available", 4.7, "Rahul", "active"));
            repo.save(new HomeService("Pipe Leakage Fix", "Plumbing", 800, "Mumbai", "Available", 4.5, "Aman", "active"));
            repo.save(new HomeService("Full House Wiring", "Electrical", 5200, "Thane", "Booked", 4.6, "Priya", "active"));
        }
    }
}
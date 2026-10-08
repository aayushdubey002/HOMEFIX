package com.homefix.api.controller;

import com.homefix.api.dto.Booking;
import com.homefix.api.dto.ServiceRequest;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class BookingController {

    private final List<Booking> bookingList = new ArrayList<>();

    public BookingController() {
        bookingList.add(new Booking("HF1001", "Aayush", "Plumbing", "Amit Services", "COMPLETED"));
        bookingList.add(new Booking("HF1002", "Arya", "AC Repair", "CoolCare Experts", "IN PROGRESS"));
        bookingList.add(new Booking("HF1003", "Piyush", "Electrical", "PowerFix", "PENDING"));
    }

    @GetMapping("/bookings/recent")
    public List<Booking> getRecentBookings() {
        return bookingList;
    }

    @PostMapping("/bookings/create")
    public Booking createBooking(@RequestBody ServiceRequest request) {
        String newId = "HF" + (1000 + (int) (Math.random() * 9000));
        Booking newBooking = new Booking(
            newId,
            request.getCustomerName(),
            request.getService(),
            "Provider will be assigned",
            "PENDING"
        );
        bookingList.add(0, newBooking);
        return newBooking;
    }
}

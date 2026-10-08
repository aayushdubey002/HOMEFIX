package com.homefix.api.controller;

import com.homefix.api.model.HomeService;
import com.homefix.api.repository.HomeServiceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class HomeServiceController {

    @Autowired
    private HomeServiceRepository serviceRepository;

    @GetMapping("/services")
    public List<HomeService> getAllServices() {
        return serviceRepository.findAll();
    }

    @PostMapping("/services")
    public HomeService addService(@RequestBody HomeService service) {
        return serviceRepository.save(service);
    }
}
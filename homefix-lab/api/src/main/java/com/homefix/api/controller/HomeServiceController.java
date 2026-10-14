package com.homefix.api.controller;

import com.homefix.api.dto.HomeServiceResponse;
import com.homefix.api.dto.ServiceCatalogItem;
import com.homefix.api.model.HomeService;
import com.homefix.api.repository.HomeServiceRepository;
import com.homefix.api.service.ServiceCatalog;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class HomeServiceController {

    private final HomeServiceRepository serviceRepository;

    public HomeServiceController(HomeServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    @GetMapping("/services")
    public List<HomeServiceResponse> getAllServices() {
        return serviceRepository.findAll().stream()
                .map(service -> new HomeServiceResponse(
                        service.getId(),
                        service.getName(),
                        service.getCategory(),
                        service.getPrice(),
                        service.getLocation(),
                        service.getAvailability(),
                        service.getRating(),
                        service.getProvider(),
                        service.getStatus()
                ))
                .toList();
    }

    @GetMapping("/services/catalog")
    public List<ServiceCatalogItem> getServiceCatalog() {
        return ServiceCatalog.items();
    }

    @PostMapping("/services")
    public HomeService addService(@RequestBody HomeService service) {
        return serviceRepository.save(service);
    }
}
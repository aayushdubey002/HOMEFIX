package com.homefix.api.controller;

import com.homefix.api.dto.HomeServiceResponse;
import com.homefix.api.dto.ServiceCatalogItem;
import com.homefix.api.model.HomeService;
import com.homefix.api.repository.HomeServiceRepository;
import com.homefix.api.repository.ServiceCategoryRepository;
import com.homefix.api.service.ServiceCatalog;
import com.homefix.api.service.ServiceCategoryImages;
import com.homefix.api.service.ServiceCategoryImageStorage;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
public class HomeServiceController {

    private final HomeServiceRepository serviceRepository;
    private final ServiceCategoryRepository categoryRepository;
    private final ServiceCategoryImageStorage categoryImageStorage;

    public HomeServiceController(
            HomeServiceRepository serviceRepository,
            ServiceCategoryRepository categoryRepository,
            ServiceCategoryImageStorage categoryImageStorage) {
        this.serviceRepository = serviceRepository;
        this.categoryRepository = categoryRepository;
        this.categoryImageStorage = categoryImageStorage;
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
        List<ServiceCatalogItem> categories = categoryRepository.findByActiveTrueOrderByNameAsc()
                .stream()
                .map(category -> new ServiceCatalogItem(
                        category.getSlug(),
                        category.getName(),
                        category.getServiceCode(),
                        category.getDescription(),
                        category.getStartingPrice(),
                        ServiceCategoryImages.resolve(
                                category.getImageKey(),
                                category.getName(),
                                category.getSlug(),
                                category.getServiceCode()),
                        category.getImageUrl()
                ))
                .toList();
        return categories.isEmpty() ? ServiceCatalog.items() : categories;
    }

    @GetMapping("/services/catalog/images/{imageKey}")
    public ResponseEntity<Resource> getServiceCategoryImage(@PathVariable String imageKey) {
        try {
            Resource image = new FileSystemResource(categoryImageStorage.resolve(imageKey));
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(categoryImageStorage.contentType(imageKey)))
                    .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                    .header("X-Content-Type-Options", "nosniff")
                    .body(image);
        } catch (IOException error) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/services")
    public HomeService addService(@RequestBody HomeService service) {
        return serviceRepository.save(service);
    }
}
package com.example.booking.tenantdata.customer;

import com.example.booking.tenantdata.Customer;
import com.example.booking.tenantdata.CustomerService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
@PreAuthorize(
        "hasAnyRole('TENANT_ADMIN', 'STAFF')"
)
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(
            CustomerService customerService) {

        this.customerService = customerService;
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> create(
            @Valid @RequestBody CreateCustomerRequest request) {

        Customer customer =
                customerService.create(request);

        CustomerResponse response =
                CustomerResponse.from(customer);

        return ResponseEntity
                .created(
                        URI.create(
                                "/api/v1/customers/"
                                        + customer.getId()
                        )
                )
                .body(response);
    }

    @GetMapping
    public List<CustomerResponse> findAll() {

        return customerService.findAll()
                .stream()
                .map(CustomerResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public CustomerResponse findById(
            @PathVariable UUID id) {

        return CustomerResponse.from(
                customerService.findById(id)
        );
    }

    @PutMapping("/{id}")
    public CustomerResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCustomerRequest request) {

        return CustomerResponse.from(
                customerService.update(id, request)
        );
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID id) {

        customerService.delete(id);
    }

    
}
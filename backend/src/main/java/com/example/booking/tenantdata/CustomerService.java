package com.example.booking.tenantdata;

import com.example.booking.tenantdata.customer.CreateCustomerRequest;
import com.example.booking.tenantdata.customer.UpdateCustomerRequest;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(
            CustomerRepository customerRepository) {

        this.customerRepository = customerRepository;
    }

    @Transactional("tenantTransactionManager")
    public Customer create(CreateCustomerRequest request) {

        Customer customer = new Customer(
                request.firstName(),
                request.lastName(),
                request.email(),
                request.phone()
        );

        return customerRepository.save(customer);
    }

    @Transactional(
            value = "tenantTransactionManager",
            readOnly = true
    )
    public List<Customer> findAll() {

        return customerRepository.findAll();
    }

    @Transactional(
            value = "tenantTransactionManager",
            readOnly = true
    )
    public Customer findById(UUID id) {

        return customerRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Customer not found"
                        )
                );
    }

    @Transactional("tenantTransactionManager")
    public Customer update(
            UUID id,
            UpdateCustomerRequest request) {

        Customer customer = customerRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Customer not found"
                        )
                );

        customer.update(
                request.firstName(),
                request.lastName(),
                request.email(),
                request.phone()
        );

        return customer;
    }

    @Transactional("tenantTransactionManager")
    public void delete(UUID id) {

        Customer customer = customerRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Customer not found"
                        )
                );

        customerRepository.delete(customer);
    }
}
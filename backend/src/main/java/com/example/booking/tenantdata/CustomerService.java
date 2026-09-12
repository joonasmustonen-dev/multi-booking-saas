package com.example.booking.tenantdata;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(
            CustomerRepository customerRepository) {

        this.customerRepository = customerRepository;
    }

    @Transactional("tenantTransactionManager")
    public Customer create(
            String firstName,
            String lastName,
            String email,
            String phone) {

        Customer customer =
                new Customer(
                        firstName,
                        lastName,
                        email,
                        phone
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
}
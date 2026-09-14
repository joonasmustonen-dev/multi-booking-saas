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

    private final com.example.booking.tenantdata.appointment.AppointmentRepository appointments;

    private final com.example.booking.tenantdata.staff.StaffMemberRepository staffRepository;

    public CustomerService(
        CustomerRepository customerRepository,
        com.example.booking.tenantdata.staff.StaffMemberRepository staffRepository,
        com.example.booking.tenantdata.appointment.AppointmentRepository appointments
    ) {
        this.customerRepository = customerRepository;

        this.staffRepository = staffRepository;

        this.appointments = appointments;
    }

    @Transactional("tenantTransactionManager")
    public Customer create(CreateCustomerRequest request) {
        Customer customer = new Customer(
            request.firstName(),
            request.lastName(),
            request.email(),
            request.phone()
        );

        applyPreferredStaff(customer, request.preferredStaffId());

        return customerRepository.save(customer);
    }

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<Customer> findAll() {
        return findPage(0, 100);
    }

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<Customer> findPage(int page, int size) {
        if (page < 0 || page > 100000 || size < 1 || size > 200) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid customer page or size (1–200)"
            );
        }
        return customerRepository
            .findDirectory(
                com.example.booking.security.ApiPermissions.isTenantAdmin(),
                org.springframework.data.domain.PageRequest.of(
                    page,
                    size,
                    org.springframework.data.domain.Sort.by("id")
                )
            )
            .getContent();
    }

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public Customer findById(UUID id) {
        Customer customer = customerRepository
            .findById(id)
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Customer not found"
                )
            );

        if (
            customer.isProcessingRestricted() &&
            !com.example.booking.security.ApiPermissions.isTenantAdmin()
        ) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Customer processing is restricted"
            );
        }
        return customer;
    }

    @Transactional("tenantTransactionManager")
    public Customer update(UUID id, UpdateCustomerRequest request) {
        Customer customer = customerRepository
            .findForUpdate(id)
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Customer not found"
                )
            );

        if (
            customer.isProcessingRestricted() &&
            !com.example.booking.security.ApiPermissions.isTenantAdmin()
        ) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Customer processing is restricted"
            );
        }

        customer.update(
            request.firstName(),
            request.lastName(),
            request.email(),
            request.phone()
        );

        applyPreferredStaff(customer, request.preferredStaffId());

        return customer;
    }

    private void applyPreferredStaff(Customer customer, UUID id) {
        if (id != null && !id.equals(customer.getPreferredStaffId())) {
            staffRepository
                .findById(id)
                .filter(staff -> staff.isActive() && !staff.isRemoved())
                .orElseThrow(() ->
                    new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Preferred staff member must be active in this workspace"
                    )
                );
        }
        customer.setPreferredStaffId(id);
    }

    @Transactional("tenantTransactionManager")
    public void delete(UUID id) {
        Customer customer = customerRepository
            .findForUpdate(id)
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Customer not found"
                )
            );

        if (customer.isLegalHold()) throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Customer data is under a legal hold"
        );

        if (
            appointments.existsByCustomer_Id(id)
        ) throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Customer has booking history and cannot be deleted here"
        );
        try {
            customerRepository.delete(customer);

            customerRepository.flush();
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Customer is referenced by a booking"
            );
        }
    }
}

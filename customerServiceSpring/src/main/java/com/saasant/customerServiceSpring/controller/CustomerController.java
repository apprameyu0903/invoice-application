
package com.saasant.customerServiceSpring.controller;

import com.saasant.customerServiceSpring.exception.CustomerNotFoundException;
import com.saasant.customerServiceSpring.service.CustomerServiceInterface;
import com.saasant.customerServiceSpring.vo.CustomerDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private static final Logger log = LoggerFactory.getLogger(CustomerController.class);

    @Autowired
    private CustomerServiceInterface customerService;

    @GetMapping("/{customerId}")
    public ResponseEntity<CustomerDetails> getCustomerById(@PathVariable String customerId) {
        log.info("API: Request to get customer by ID: {}", customerId);
        CustomerDetails customer = customerService.getCustomerById(customerId);
        log.debug("API: Customer found: {}", customerId);
        return ResponseEntity.ok(customer);
    }
    
    @GetMapping("/search/{query}") 
    public ResponseEntity<Page<CustomerDetails>> searchCustomers(
            @PathVariable String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "7") int size,
            @RequestParam(defaultValue = "customerId,asc") String[] sort) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(sort[0]));
        Page<CustomerDetails> customersPage = customerService.searchCustomers(query, pageable);
        return ResponseEntity.ok(customersPage);
    }

    @GetMapping("/page")
    public ResponseEntity<Page<CustomerDetails>> getAllCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "7") int size,
            @RequestParam(defaultValue = "customerId,asc") String[] sort) {


	Pageable pageable = PageRequest.of(page, size, Sort.by(sort[0]));
        Page<CustomerDetails> customersPage = customerService.getAllCustomers(pageable);
        return ResponseEntity.ok(customersPage);
    }
    
    @GetMapping
    public ResponseEntity<List<CustomerDetails>> getAllCustomers() {
        log.info("API: Request to get all customers");
        List<CustomerDetails> customers = customerService.fetchAllCustomers();
        return ResponseEntity.ok(customers);
    }

    @PostMapping
    public ResponseEntity<CustomerDetails> addCustomer(@RequestBody CustomerDetails customerDetails) {
        log.info("API: Request to add new customer: {}", customerDetails.getCustomerId());
        CustomerDetails newCustomer = customerService.addCustomer(customerDetails);
        if (newCustomer != null && newCustomer.getCustomerId() != null) {
            log.info("API: Customer added successfully with ID: {}", newCustomer.getCustomerId());
            return ResponseEntity.status(HttpStatus.CREATED).body(newCustomer);
        } else {
            log.warn("API: Failed to add customer. Input: {}", customerDetails.getCustomerId());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    @PutMapping("/{customerId}")
    public ResponseEntity<CustomerDetails> updateCustomer(@PathVariable String customerId, @RequestBody CustomerDetails customerDetails) {
        log.info("API: Request to update customer with ID: {}", customerId);
        customerDetails.setCustomerId(customerId);
        CustomerDetails updatedCustomer = customerService.updateCustomer(customerId, customerDetails);
        if (updatedCustomer != null) {
            log.info("API: Customer updated successfully: {}", customerId);
            return ResponseEntity.ok(updatedCustomer);
        } else {
            log.warn("API: Failed to update customer (other than not found): {}", customerId);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @DeleteMapping("/{customerId}")
    public ResponseEntity<String> deleteCustomer(@PathVariable String customerId) {
        log.info("API: Request to delete customer with ID: {}", customerId);
        customerService.deleteCustomer(customerId);
        log.info("API: Customer deletion processed for ID: {}", customerId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body("Customer with ID " + customerId + " deleted successfully.");
    }
}

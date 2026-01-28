package com.saasant.invoiceServiceSpring.controller;

import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import com.saasant.invoiceServiceSpring.service.CustomerClientService;
import com.saasant.invoiceServiceSpring.service.EmployeeClientService;
//import com.saasant.invoiceServiceSpring.service.InvoiceClientService;
import com.saasant.invoiceServiceSpring.service.InvoiceClientServiceInterface;
import com.saasant.invoiceServiceSpring.service.ProductClientService;
import com.saasant.invoiceServiceSpring.vo.CustomerDetails;
import com.saasant.invoiceServiceSpring.vo.Employee;
import com.saasant.invoiceServiceSpring.vo.InvoiceDetails;
import com.saasant.invoiceServiceSpring.vo.InvoiceItem;
import com.saasant.invoiceServiceSpring.vo.Product;
import com.saasant.invoiceServiceSpring.dao.InvoiceDao;
import com.saasant.invoiceServiceSpring.entity.Invoice;
import com.saasant.invoiceServiceSpring.exception.CustomerNotFoundException;
import com.saasant.invoiceServiceSpring.exception.EmployeeNotFoundException;
import com.saasant.invoiceServiceSpring.exception.ProductNotFoundException;

@CrossOrigin
@RestController
@RequestMapping("/api/invoice")
public class InvoiceController {
	
	private static final Logger log = LoggerFactory.getLogger(InvoiceController.class);
	
	@Autowired
	CustomerClientService customerClientService;
	
	@Autowired
	EmployeeClientService employeeClientService;
	
	@Autowired
	ProductClientService productClientService;
	
	@Autowired
	InvoiceClientServiceInterface invoiceClientService;
	
	
	@GetMapping("/products/refresh-cache")
    public ResponseEntity<String> refreshProductCache() {
        log.info("Request received to refresh product cache.");
        try {
            productClientService.loadProductsIntoCache(); 
            log.info("Product cache refreshed successfully.");
            return ResponseEntity.ok("Product cache refreshed successfully.");
        } catch (Exception e) {
            log.error("Error occurred while refreshing product cache: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error occurred while refreshing product cache: " + e.getMessage());
        }
    }
    
    @GetMapping("/products/cache/status")
    public ResponseEntity<Map<String, Object>> getProductCacheStatus() {
        log.info("Request received to get product cache status.");
        try {
            Map<String, Object> status = new LinkedHashMap<>();
            status.put("cacheEmpty", productClientService.isCacheEmpty());
            status.put("cacheSize", productClientService.getCacheSize());
            status.put("timestamp", java.time.LocalDateTime.now());
            
            log.debug("Product cache status retrieved: {}", status);
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Error occurred while getting product cache status: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error occurred while getting product cache status: " + e.getMessage()));
        }
    }
    
    @GetMapping("/products/cache/all")
    public ResponseEntity<Map<String, Object>> getAllCachedProducts() {
        log.info("Request received to get all cached products.");
        try {
            List<Product> products = productClientService.getAllCachedProducts();
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("cacheSize", products.size());
            response.put("products", products);
            response.put("timestamp", java.time.LocalDateTime.now());
            
            log.debug("Retrieved {} products from cache", products.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error occurred while getting cached products: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error occurred while getting cached products: " + e.getMessage()));
        }
    }
	
	@GetMapping
	public ResponseEntity<List<InvoiceDetails>> getInvoices(){
		log.info("Request to fetch invoices");
		
		List<InvoiceDetails> invoices = invoiceClientService.fetchInvoices();
		log.info("Invoices fetched");

		return ResponseEntity.ok(invoices);
		
	}
	
	

	@GetMapping("/{invoiceId}")
    public ResponseEntity<?> getInvoiceById(@PathVariable String invoiceId) {
		log.info("Request to fetch invoice with ID: {}", invoiceId);
        Optional<InvoiceDetails> invoiceDetailsOpt = invoiceClientService.getInvoiceById(invoiceId);

        if (invoiceDetailsOpt.isPresent()) {
            InvoiceDetails invoiceDetails = invoiceDetailsOpt.get();
            log.info("Invoice found: {}", invoiceId);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("invoiceDetails", invoiceDetails);

            try {
                Optional<CustomerDetails> customerDetailsOpt = customerClientService.getCustomerById(invoiceDetails.getCustomerId());
                customerDetailsOpt.ifPresentOrElse(
                    customer -> response.put("customerDetails", customer),
                    () -> {
                        log.warn("Customer details not found for customer ID: {}", invoiceDetails.getCustomerId());
                        response.put("customerDetails", "Customer not found with ID: " + invoiceDetails.getCustomerId());
                    }
                );
            } catch (CustomerNotFoundException e) {
                 log.warn("CustomerNotFoundException while fetching customer ID {}: {}", invoiceDetails.getCustomerId(), e.getMessage());
                 response.put("customerDetails", e.getMessage());
            } catch (Exception e) {
                log.error("Error fetching customer details for customer ID {}: {}", invoiceDetails.getCustomerId(), e.getMessage(), e);
                response.put("customerDetails", "Error fetching customer details.");
            }


            try {
                Optional<Employee> employeeDetailsOpt = employeeClientService.getEmployeeById(invoiceDetails.getEmployeeId());
                employeeDetailsOpt.ifPresentOrElse(
                    employee -> response.put("employeeDetails", employee),
                    () -> {
                        log.warn("Employee details not found for employee ID: {}", invoiceDetails.getEmployeeId());
                        response.put("employeeDetails", "Employee not found with ID: " + invoiceDetails.getEmployeeId());
                    }
                );
            } catch (EmployeeNotFoundException e) {
                log.warn("EmployeeNotFoundException while fetching employee ID {}: {}", invoiceDetails.getEmployeeId(), e.getMessage());
                response.put("employeeDetails", e.getMessage());
            } catch (Exception e) {
                log.error("Error fetching employee details for employee ID {}: {}", invoiceDetails.getEmployeeId(), e.getMessage(), e);
                response.put("employeeDetails", "Error fetching employee details.");
            }
           
            return ResponseEntity.ok(response);
        } else {
            log.warn("Invoice not found with ID: {}", invoiceId);
             return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Invoice not found with Id: " + invoiceId);
        }
    }
	
	@PostMapping
    public ResponseEntity<String> createInvoice(@RequestBody InvoiceDetails invoiceDetails) {
        log.info("Received request to create invoice for customer ID: {}", invoiceDetails.getCustomerId());

        // 1. Validate Customer
        String customerId = invoiceDetails.getCustomerId();
        if (customerId == null || customerId.trim().isEmpty()) {
            log.warn("Customer ID is missing in the request.");
            return ResponseEntity.badRequest().body("Customer ID is required.");
        }
        // The CustomerClientService from Canvas returns Optional<CustomerDetailsVO>
        Optional<CustomerDetails> customerOpt = customerClientService.getCustomerById(customerId);
        if (customerOpt.isEmpty()) {
            log.warn("Customer validation failed for ID: {}. Customer not found or error in service call.", customerId);
            throw new CustomerNotFoundException("Customer ID : " + customerId + " cannot be found" );
        }
        CustomerDetails validatedCustomer = customerOpt.get();
        log.info("Customer {} validated successfully: {}", customerId, validatedCustomer.getCustomerName());

        // 2. Validate Employee
        String employeeId = invoiceDetails.getEmployeeId();
        if (employeeId == null || employeeId.trim().isEmpty()) {
            log.warn("Employee ID is missing in the request.");
            return ResponseEntity.badRequest().body("Employee ID is required.");
        }
        Optional<Employee> employeeOpt = employeeClientService.getEmployeeById(employeeId); // Assumes this service and VO exist
        if (employeeOpt.isEmpty()) {
            log.warn("Employee validation failed for ID: {}. Employee not found or error in service call.", employeeId);
            throw new EmployeeNotFoundException("Employee ID : " + employeeId + " cannot be found");
        }
        Employee validatedEmployee = employeeOpt.get();
        log.info("Employee {} validated successfully: {}", employeeId, validatedEmployee.getEmpName());


        // 3. Validate Products
        if (invoiceDetails.getItems() == null || invoiceDetails.getItems().isEmpty()) {
            log.warn("Invoice items are missing or empty.");
            return ResponseEntity.badRequest().body("Invoice must contain at least one item.");
        }
        for (InvoiceItem item : invoiceDetails.getItems()) {
            if (item.getProductId() == null || item.getProductId().trim().isEmpty()) {
                log.warn("Product ID is missing for an item.");
                return ResponseEntity.badRequest().body("Product ID is missing for an item.");
            }
            int productIdInt;
            try {
                productIdInt = Integer.parseInt(item.getProductId()); // Assuming product ID in item is String, but ProductService uses int
            } catch (NumberFormatException e) {
                log.warn("Invalid Product ID format for item: {}", item.getProductId());
                return ResponseEntity.badRequest().body("Invalid Product ID format: " + item.getProductId());
            }

            // Assumes ProductClientService.isValidProduct and getProductById exist and use int for product ID
            if (!productClientService.isValidProduct(productIdInt)) {
               log.warn("Invalid product ID {} found in invoice items (not in cache or service).", productIdInt);
               throw new ProductNotFoundException("Product ID : " + productIdInt + " cannot be found");
            }
            Product productOpt = productClientService.getProductById(productIdInt);
            if(productOpt != null){
                Product validatedProduct = productOpt;
                log.info("Product {} (ID: {}) validated. Price from service: {}", validatedProduct.getName(), productIdInt, validatedProduct.getPrice());
                double basePrice = validatedProduct.getPrice();
                double taxPercent = validatedProduct.getTaxPercent();
                double priceWithTax = basePrice + (basePrice * (taxPercent / 100.0));
                item.setPricePerUnit((float) priceWithTax);
            } else {
                log.warn("Product ID {} was marked valid but details not found. Possible inconsistency.", productIdInt);
                throw new ProductNotFoundException("Product ID : " + productIdInt + " cannot be found");
            }
        }
        log.info("All product items validated successfully.");
        
        if (invoiceDetails.getItems() != null && !invoiceDetails.getItems().isEmpty()) {
            Map<String, InvoiceItem> consolidatedItemsMap = new LinkedHashMap<>(); 
            for (InvoiceItem currentItem : invoiceDetails.getItems()) {
                String productId = currentItem.getProductId();
                if (consolidatedItemsMap.containsKey(productId)) {
                    InvoiceItem existingItem = consolidatedItemsMap.get(productId);
                    existingItem.setQuantity(existingItem.getQuantity() + currentItem.getQuantity());
                } else {
                    consolidatedItemsMap.put(productId, currentItem);
                }
            }
            invoiceDetails.setItems(new ArrayList<>(consolidatedItemsMap.values()));
            log.info("Invoice items consolidated. Number of unique items: {}", invoiceDetails.getItems().size());
        }


        // 4. Set Invoice Number and Dates
        if (invoiceDetails.getInvoiceNumber() == null || invoiceDetails.getInvoiceNumber().trim().isEmpty()) {
        	LocalDate today = LocalDate.now();
            DateTimeFormatter dtfDate = DateTimeFormatter.ofPattern("ddMMYYYY");
            String formattedDatePart = today.format(dtfDate);
            long countForToday = invoiceClientService.getInvoiceCount(today); //
            long nextSequence = countForToday + 1;
            String formattedSequence = String.format("%03d", nextSequence);

            invoiceDetails.setInvoiceNumber("INV-" + formattedDatePart + "-" + formattedSequence);
            log.info("Generated new invoice number: {}", invoiceDetails.getInvoiceNumber());
        }
        if(invoiceDetails.getInvoiceId() == null || invoiceDetails.getInvoiceId().trim().isEmpty()) {
        	invoiceDetails.setInvoiceId(UUID.randomUUID().toString());
        }
        if (invoiceDetails.getInvoiceDate() == null) {
            invoiceDetails.setInvoiceDate(java.time.LocalDateTime.now());
        }
        if (invoiceDetails.getDueDate() == null) {
            invoiceDetails.setDueDate(java.time.LocalDate.now().plusDays(30));
        }

        // 5. Calculate Total Amount (ensure this uses the potentially updated item prices if you override them)
        invoiceDetails.calculateTotalAmount();
        log.info("Calculated total bill amount: {}", invoiceDetails.getTotalAmount());
        try {
        	Invoice savedInvoice = invoiceClientService.saveInvoice(invoiceDetails);
            log.info("Invoice processing complete for invoice number: {}", invoiceDetails.getInvoiceNumber());
            String responseMessage = String.format("Invoice %s created successfully for customer %s, by employee %s. Total: %.2f. %d item(s) processed.",
                    savedInvoice.getInvoiceNumber(),
                    validatedCustomer.getCustomerName(),
                    validatedEmployee.getEmpName(),
                    savedInvoice.getTotalAmount(),
                    invoiceDetails.getItems().size()
                    );
            return ResponseEntity.status(HttpStatus.CREATED).body(responseMessage);

        } catch (Exception e) {
            log.error("Error during invoice processing for customer {}: {}", customerId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An error occurred while processing the invoice.");
        }
    }
	
	@PutMapping("/{invoiceId}")
	public ResponseEntity<String> editInvoice(@PathVariable String invoiceId, @RequestBody InvoiceDetails invoiceDetails){
		log.info("Request to edit invoice with ID: {}", invoiceId);
		String customerId = invoiceDetails.getCustomerId();
        if (customerId == null || customerId.trim().isEmpty()) {
            log.warn("Customer ID is missing in the request.");
            return ResponseEntity.badRequest().body("Customer ID is required.");
        }

        Optional<CustomerDetails> customerOpt = customerClientService.getCustomerById(customerId);
        if (customerOpt.isEmpty()) {
            log.warn("Customer validation failed for ID: {}. Customer not found or error in service call.", customerId);
            throw new CustomerNotFoundException("Customer ID : " + customerId + " cannot be found" );
        }
        CustomerDetails validatedCustomer = customerOpt.get();
        log.info("Customer {} validated successfully: {}", customerId, validatedCustomer.getCustomerName());

        String employeeId = invoiceDetails.getEmployeeId();
        if (employeeId == null || employeeId.trim().isEmpty()) {
            log.warn("Employee ID is missing in the request.");
            return ResponseEntity.badRequest().body("Employee ID is required.");
        }
        Optional<Employee> employeeOpt = employeeClientService.getEmployeeById(employeeId); 
        if (employeeOpt.isEmpty()) {
            log.warn("Employee validation failed for ID: {}. Employee not found or error in service call.", employeeId);
            throw new EmployeeNotFoundException("Employee ID : " + employeeId + " cannot be found");
        }
        Employee validatedEmployee = employeeOpt.get();
        log.info("Employee {} validated successfully: {}", employeeId, validatedEmployee.getEmpName());

        if (invoiceDetails.getItems() == null || invoiceDetails.getItems().isEmpty()) {
            log.warn("Invoice items are missing or empty.");
            return ResponseEntity.badRequest().body("Invoice must contain at least one item.");
        }
        for (InvoiceItem item : invoiceDetails.getItems()) {
            if (item.getProductId() == null || item.getProductId().trim().isEmpty()) {
                log.warn("Product ID is missing for an item.");
                return ResponseEntity.badRequest().body("Product ID is missing for an item.");
            }
            int productIdInt;
            try {
                productIdInt = Integer.parseInt(item.getProductId()); 
            } catch (NumberFormatException e) {
                log.warn("Invalid Product ID format for item: {}", item.getProductId());
                return ResponseEntity.badRequest().body("Invalid Product ID format: " + item.getProductId());
            }
            if (!productClientService.isValidProduct(productIdInt)) {
               log.warn("Invalid product ID {} found in invoice items (not in cache or service).", productIdInt);
               throw new ProductNotFoundException("Product ID : " + productIdInt + " cannot be found");
            }
            Product productOpt = productClientService.getProductById(productIdInt);
            if(productOpt != null){
                Product validatedProduct = productOpt;
                log.info("Product {} (ID: {}) validated. Price from service: {}", validatedProduct.getName(), productIdInt, validatedProduct.getPrice());
                double basePrice = validatedProduct.getPrice();
                double taxPercent = validatedProduct.getTaxPercent();
                double priceWithTax = basePrice + (basePrice * (taxPercent / 100.0));
                item.setPricePerUnit((float) priceWithTax);
            } else {
                log.warn("Product ID {} was marked valid but details not found. Possible inconsistency.", productIdInt);
                throw new ProductNotFoundException("Product ID : " + productIdInt + " cannot be found");
            }
        }
        log.info("All product items validated successfully.");
        
        if (invoiceDetails.getItems() != null && !invoiceDetails.getItems().isEmpty()) {
            Map<String, InvoiceItem> consolidatedItemsMap = new LinkedHashMap<>(); 
            for (InvoiceItem currentItem : invoiceDetails.getItems()) {
                String productId = currentItem.getProductId();
                if (consolidatedItemsMap.containsKey(productId)) {
                    InvoiceItem existingItem = consolidatedItemsMap.get(productId);
                    existingItem.setQuantity(existingItem.getQuantity() + currentItem.getQuantity());
                } else {
                    consolidatedItemsMap.put(productId, currentItem);
                }
            }
            invoiceDetails.setItems(new ArrayList<>(consolidatedItemsMap.values()));
            log.info("Invoice items consolidated. Number of unique items: {}", invoiceDetails.getItems().size());
        }
        
        if (invoiceDetails.getInvoiceNumber() == null || invoiceDetails.getInvoiceNumber().trim().isEmpty()) {
        	log.error("Enter valid Invoice Number");
        }
        if(invoiceDetails.getInvoiceId() == null || invoiceDetails.getInvoiceId().trim().isEmpty()) {
        	log.error("Enter valid Invoice Id");
        }

        invoiceDetails.calculateTotalAmount();
        log.info("Calculated total bill amount: {}", invoiceDetails.getTotalAmount());
        try {
        	Invoice savedInvoice = invoiceClientService.updateInvoice(invoiceId, invoiceDetails);
            log.info("Invoice processing complete for invoice number: {}", invoiceDetails.getInvoiceNumber());
            String responseMessage = String.format("Invoice %s edited successfully for customer %s, by employee %s. Total: %.2f. %d item(s) processed.",
                    savedInvoice.getInvoiceNumber(),
                    validatedCustomer.getCustomerName(),
                    validatedEmployee.getEmpName(),
                    savedInvoice.getTotalAmount(),
                    invoiceDetails.getItems().size()
                    );
            return ResponseEntity.status(HttpStatus.OK).body(responseMessage);

        } catch (Exception e) {
            log.error("Error during invoice processing for customer {}: {}", customerId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An error occurred while processing the invoice.");
        }
	}
	
	@DeleteMapping("/{invoiceId}")
	public ResponseEntity<String> deleteInvoice(@PathVariable String invoiceId){
		
		invoiceClientService.deleteInvoiceById(invoiceId);
		return ResponseEntity.status(HttpStatus.ACCEPTED).body("Invoice Deleted");
	}
	
	
	

	

}

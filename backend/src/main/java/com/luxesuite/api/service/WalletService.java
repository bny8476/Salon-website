package com.luxesuite.api.service;
import com.luxesuite.api.dto.WalletDto;
import com.luxesuite.api.model.Customer;
import com.luxesuite.api.model.Invoice;
import com.luxesuite.api.model.Wallet;
import com.luxesuite.api.model.WalletTransaction;
import com.luxesuite.api.repository.CustomerRepository;
import com.luxesuite.api.repository.InvoiceRepository;
import com.luxesuite.api.repository.WalletRepository;
import com.luxesuite.api.repository.WalletTransactionRepository;
import com.luxesuite.api.exception.ResourceNotFoundException;
import com.luxesuite.api.exception.BadRequestException;
import com.luxesuite.api.exception.PaymentGatewayException;
import com.luxesuite.api.security.SecurityUtils;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import com.razorpay.RazorpayClient;
import com.razorpay.Order;
import com.razorpay.Utils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.Optional;
import io.micrometer.core.instrument.MeterRegistry;

@Service
@RequiredArgsConstructor
public class WalletService {
    
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;
    private final SecurityUtils securityUtils;
    private final RazorpayClient razorpayClient;
    private final MeterRegistry meterRegistry;
    
    @Value("${razorpay.key.secret:dummy_secret}")
    private String razorpaySecret;

    @Transactional(readOnly = true)
    public WalletDto getMyWallet() {
        Long userId = securityUtils.getCurrentUserId();
        Customer customer = customerRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found"));
                
        Wallet wallet = walletRepository.findByCustomerId(customer.getId())
                .orElseGet(() -> createEmptyWallet(customer));
        return mapToDto(wallet);
    }

    @Transactional
    public Wallet createEmptyWallet(Customer customer) {
        Wallet wallet = Wallet.builder()
                .customer(customer)
                .balance(BigDecimal.ZERO)
                .build();
                
        return walletRepository.save(wallet);
    }

    public String createTopupPaymentIntent(BigDecimal amount) {
        Long customerId = securityUtils.getCurrentUserId();
        try {
            PaymentIntentCreateParams params =
                PaymentIntentCreateParams.builder()
                    .setAmount(amount.multiply(new BigDecimal(100)).longValue())
                    .setCurrency("inr")
                    .putMetadata("walletTopupCustomerId", customerId.toString())
                    .build();

            PaymentIntent intent = PaymentIntent.create(params);
            return intent.getClientSecret();
        } catch (Exception e) {
            meterRegistry.counter("wallet.topup.failure", "gateway", "stripe").increment();
            throw new PaymentGatewayException("Failed to create Stripe PaymentIntent for Wallet Topup: " + e.getMessage(), e);
        }
    }

    public void mockTopup(BigDecimal amount) {
        Long customerId = securityUtils.getCurrentUserId();
        processTopup(customerId, amount, "MOCK-" + System.currentTimeMillis());
    }

    public String createRazorpayTopupOrder(BigDecimal amount) {
        Long customerId = securityUtils.getCurrentUserId();
        try {
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amount.multiply(new BigDecimal(100)).longValue());
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", "wallet_" + customerId + "_" + System.currentTimeMillis());
            
            JSONObject notes = new JSONObject();
            notes.put("walletTopupCustomerId", customerId.toString());
            notes.put("type", "WALLET_TOPUP");
            orderRequest.put("notes", notes);

            Order order = razorpayClient.orders.create(orderRequest);
            return order.get("id");
        } catch (Exception e) {
            meterRegistry.counter("wallet.topup.failure", "gateway", "razorpay").increment();
            throw new PaymentGatewayException("Failed to create Razorpay Order for Wallet Topup: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void verifyRazorpayTopup(String paymentId, String orderId, String signature, BigDecimal amount) {
        try {
            JSONObject options = new JSONObject();
            options.put("razorpay_order_id", orderId);
            options.put("razorpay_payment_id", paymentId);
            options.put("razorpay_signature", signature);
            
            boolean isValid = Utils.verifyPaymentSignature(options, razorpaySecret);
            if (!isValid) {
                throw new BadRequestException("Invalid payment signature");
            }
            Long customerId = securityUtils.getCurrentUserId();
            processTopup(customerId, amount, paymentId);
        } catch (Exception e) {
            meterRegistry.counter("wallet.topup.failure", "gateway", "razorpay").increment();
            if (e instanceof BadRequestException) throw (BadRequestException) e;
            throw new BadRequestException("Signature verification failed: " + e.getMessage());
        }
    }

    @Transactional
    public void processTopup(Long userId, BigDecimal amount, String transactionRef) {
        Customer customer = customerRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        Wallet wallet = walletRepository.findByCustomerIdForUpdate(customer.getId())
                .orElseGet(() -> createEmptyWallet(customer));
                
        wallet.setBalance(wallet.getBalance().add(amount));
        Wallet saved = walletRepository.save(wallet);
        
        WalletTransaction tx = WalletTransaction.builder()
                .customer(saved.getCustomer())
                .amount(amount)
                .type("TOPUP")
                .build();
        walletTransactionRepository.save(tx);
        meterRegistry.counter("wallet.topup.success").increment();
    }



    @Transactional
    public BigDecimal debit(Long customerId, BigDecimal amount, Long invoiceId) {
        Wallet wallet = walletRepository.findByCustomerIdForUpdate(customerId)
                .orElseThrow(() -> new BadRequestException("Wallet not found"));
                
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new BadRequestException("Insufficient wallet balance");
        }
        
        Invoice invoice = null;
        if (invoiceId != null) {
            invoice = invoiceRepository.findById(invoiceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
        }
        
        wallet.setBalance(wallet.getBalance().subtract(amount));
        Wallet saved = walletRepository.save(wallet);
        
        WalletTransaction tx = WalletTransaction.builder()
                .customer(saved.getCustomer())
                .amount(amount)
                .type("DEBIT")
                .relatedInvoice(invoice)
                .build();
        walletTransactionRepository.save(tx);
        
        return saved.getBalance();
    }
    
    @Transactional
    public void credit(Long customerId, BigDecimal amount, String reason) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        Wallet wallet = walletRepository.findByCustomerIdForUpdate(customerId)
                .orElseGet(() -> createEmptyWallet(customer));
                
        wallet.setBalance(wallet.getBalance().add(amount));
        Wallet saved = walletRepository.save(wallet);
        
        WalletTransaction tx = WalletTransaction.builder()
                .customer(saved.getCustomer())
                .amount(amount)
                .type("REFUND") // Could use custom reason but keeping it simple
                .build();
        walletTransactionRepository.save(tx);
    }

    private WalletDto mapToDto(Wallet wallet) {
        WalletDto dto = new WalletDto();
        dto.setId(wallet.getId());
        dto.setCustomerId(wallet.getCustomer().getId());
        dto.setBalance(wallet.getBalance());
        return dto;
    }
}

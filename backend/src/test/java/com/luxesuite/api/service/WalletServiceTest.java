package com.luxesuite.api.service;

import com.luxesuite.api.dto.WalletDto;
import com.luxesuite.api.model.Customer;
import com.luxesuite.api.model.Wallet;
import com.luxesuite.api.repository.CustomerRepository;
import com.luxesuite.api.repository.WalletRepository;
import com.luxesuite.api.repository.WalletTransactionRepository;
import com.luxesuite.api.security.SecurityUtils;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import com.luxesuite.api.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletTransactionRepository walletTransactionRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private SecurityUtils securityUtils;

    @Mock
    private RazorpayClient razorpayClient;

    @InjectMocks
    private WalletService walletService;

    private Customer testCustomer;
    private Wallet testWallet;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(walletService, "razorpaySecret", "test_secret");
        ReflectionTestUtils.setField(walletService, "meterRegistry", new io.micrometer.core.instrument.simple.SimpleMeterRegistry());

        User user = new User();
        user.setId(100L);

        testCustomer = new Customer();
        testCustomer.setId(1L);
        testCustomer.setUser(user);

        testWallet = new Wallet();
        testWallet.setId(10L);
        testWallet.setCustomer(testCustomer);
        testWallet.setBalance(new BigDecimal("500.00"));
    }

    @Test
    void getMyWallet_ReturnsCorrectBalance() {
        when(securityUtils.getCurrentUserId()).thenReturn(100L);
        when(customerRepository.findByUserId(100L)).thenReturn(Optional.of(testCustomer));
        when(walletRepository.findByCustomerId(1L)).thenReturn(Optional.of(testWallet));

        WalletDto result = walletService.getMyWallet();

        assertNotNull(result);
        assertEquals(1L, result.getCustomerId());
        assertEquals(new BigDecimal("500.00"), result.getBalance());
        
        verify(securityUtils).getCurrentUserId();
        verify(customerRepository).findByUserId(100L);
        verify(walletRepository).findByCustomerId(1L);
    }

    @Test
    void getMyWallet_CreatesEmptyWalletIfNotFound() {
        when(securityUtils.getCurrentUserId()).thenReturn(100L);
        when(customerRepository.findByUserId(100L)).thenReturn(Optional.of(testCustomer));
        when(walletRepository.findByCustomerId(1L)).thenReturn(Optional.empty());
        
        Wallet emptyWallet = new Wallet();
        emptyWallet.setId(11L);
        emptyWallet.setCustomer(testCustomer);
        emptyWallet.setBalance(BigDecimal.ZERO);
        
        when(walletRepository.save(any(Wallet.class))).thenReturn(emptyWallet);

        WalletDto result = walletService.getMyWallet();

        assertNotNull(result);
        assertEquals(1L, result.getCustomerId());
        assertEquals(BigDecimal.ZERO, result.getBalance());
        verify(walletRepository).save(any(Wallet.class));
    }
    
    @Test
    void verifyRazorpayTopup_Success() {
        when(securityUtils.getCurrentUserId()).thenReturn(100L);
        when(customerRepository.findByUserId(100L)).thenReturn(Optional.of(testCustomer));
        when(walletRepository.findByCustomerIdForUpdate(1L)).thenReturn(Optional.of(testWallet));
        when(walletRepository.save(any(Wallet.class))).thenReturn(testWallet);
        
        try (MockedStatic<Utils> utilsMockedStatic = mockStatic(Utils.class)) {
            utilsMockedStatic.when(() -> Utils.verifyPaymentSignature(any(), eq("test_secret"))).thenReturn(true);
            
            walletService.verifyRazorpayTopup("pay_123", "order_123", "sig_123", new BigDecimal("100.00"));
            
            verify(walletRepository).save(testWallet);
            verify(walletTransactionRepository).save(any());
            
            // Check that balance was updated
            assertEquals(new BigDecimal("600.00"), testWallet.getBalance());
        }
    }
}

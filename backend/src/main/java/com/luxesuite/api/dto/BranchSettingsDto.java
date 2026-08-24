package com.luxesuite.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.luxesuite.api.model.BranchSettings;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchSettingsDto {

    private Long id;
    private Long branchId;
    private String businessName;
    private String email;
    private String phone;
    private String currency;
    private String timeZone;
    private boolean maintenanceMode;
    
    // Public keys only, do NOT include secrets!
    private String stripePublicKey;
    private String razorpayKeyId;
    private String brandLogoUrl;
    private String primaryColor;
    
    public static BranchSettingsDto fromEntity(BranchSettings entity) {
        if (entity == null) return null;
        
        return BranchSettingsDto.builder()
                .id(entity.getId())
                .branchId(entity.getBranch() != null ? entity.getBranch().getId() : null)
                .businessName(entity.getBusinessName())
                .email(entity.getEmail())
                .phone(entity.getPhone())
                .currency(entity.getCurrency())
                .timeZone(entity.getTimeZone())
                .maintenanceMode(entity.isMaintenanceMode())
                .stripePublicKey(entity.getStripePublicKey())
                .razorpayKeyId(entity.getRazorpayKeyId())
                .brandLogoUrl(entity.getBrandLogoUrl())
                .primaryColor(entity.getPrimaryColor())
                .build();
    }
}

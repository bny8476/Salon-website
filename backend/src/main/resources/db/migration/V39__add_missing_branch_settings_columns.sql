ALTER TABLE branch_settings ADD COLUMN stripe_public_key VARCHAR(255);
ALTER TABLE branch_settings ADD COLUMN stripe_secret_key VARCHAR(255);
ALTER TABLE branch_settings ADD COLUMN razorpay_key_id VARCHAR(255);
ALTER TABLE branch_settings ADD COLUMN razorpay_key_secret VARCHAR(255);
ALTER TABLE branch_settings ADD COLUMN whatsapp_api_key VARCHAR(255);
ALTER TABLE branch_settings ADD COLUMN whatsapp_phone_number_id VARCHAR(255);
ALTER TABLE branch_settings ADD COLUMN brand_logo_url VARCHAR(255);
ALTER TABLE branch_settings ADD COLUMN primary_color VARCHAR(50);

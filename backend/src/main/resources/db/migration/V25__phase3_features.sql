-- 1. Add fields to branches
ALTER TABLE branches ADD COLUMN phone VARCHAR(20);
ALTER TABLE branches ADD COLUMN is_active BOOLEAN DEFAULT TRUE;

-- Backfill Main Branch if none exist
INSERT INTO branches (name, address, is_active)
SELECT 'Main Branch', '123 Main St', TRUE
WHERE NOT EXISTS (SELECT 1 FROM branches);

-- Update existing users with null branch_id to use the Main Branch
UPDATE users 
SET branch_id = (SELECT id FROM branches ORDER BY id ASC LIMIT 1)
WHERE branch_id IS NULL;

-- Update existing staff with null branch_id to use the Main Branch
UPDATE staff 
SET branch_id = (SELECT id FROM branches ORDER BY id ASC LIMIT 1)
WHERE branch_id IS NULL;


-- 2. WhatsApp Log
CREATE TABLE whatsapp_message_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT,
    phone_number VARCHAR(20) NOT NULL,
    template_name VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL, -- SENT, FAILED, SKIPPED_NO_PROVIDER
    related_entity_type VARCHAR(100),
    related_entity_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE SET NULL
);


-- 3. WhatsApp Opt-in
ALTER TABLE customers 
ADD COLUMN whatsapp_opt_in BOOLEAN DEFAULT FALSE;


-- 4. CMS Content Blocks
CREATE TABLE cms_content_blocks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    page_key VARCHAR(100) NOT NULL,
    block_key VARCHAR(100) NOT NULL,
    content_type VARCHAR(50) NOT NULL, -- TEXT, RICH_TEXT, IMAGE_URL
    content_value TEXT,
    updated_by BIGINT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_page_block (page_key, block_key),
    FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL
);

-- Seed data for Home Page (matching current Home.tsx)
INSERT INTO cms_content_blocks (page_key, block_key, content_type, content_value) VALUES
('home', 'hero_headline', 'TEXT', 'Elevate Your Natural Beauty'),
('home', 'hero_subtext', 'TEXT', 'Experience luxury treatments tailored specifically for you in our serene sanctuary.'),
('home', 'hero_image_url', 'IMAGE_URL', 'https://images.unsplash.com/photo-1544161515-4ab6ce6db874?ixlib=rb-4.0.3&auto=format&fit=crop&w=2850&q=80'),
('home', 'about_headline', 'TEXT', 'Our Philosophy'),
('home', 'about_subtext', 'TEXT', 'We believe that beauty is an expression of inner wellness. Our expert team combines advanced techniques with premium products to deliver exceptional results in a deeply relaxing environment.');

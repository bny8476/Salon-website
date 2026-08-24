ALTER TABLE appointments ADD COLUMN deposit_amount DECIMAL(10,2) DEFAULT 0;
ALTER TABLE appointments ADD COLUMN is_deposit_paid BOOLEAN DEFAULT FALSE;
ALTER TABLE appointments ADD COLUMN reminder_24h_sent BOOLEAN DEFAULT FALSE;
ALTER TABLE appointments ADD COLUMN reminder_2h_sent BOOLEAN DEFAULT FALSE;
ALTER TABLE appointments ADD COLUMN rebooking_nudge_sent BOOLEAN DEFAULT FALSE;

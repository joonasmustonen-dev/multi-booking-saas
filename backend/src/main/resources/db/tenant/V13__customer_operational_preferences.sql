ALTER TABLE customers ADD COLUMN preferred_staff_id uuid REFERENCES staff_members(id) ON DELETE SET NULL;
CREATE INDEX idx_customer_appointments_history ON appointments(customer_id, start_at DESC, id);

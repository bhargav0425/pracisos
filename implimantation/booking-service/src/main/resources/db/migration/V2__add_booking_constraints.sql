-- Ensure slot status consistency via trigger
CREATE OR REPLACE FUNCTION check_slot_booking_consistency()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status = 'CONFIRMED' THEN
        UPDATE time_slots SET status = 'BOOKED', version = version + 1
        WHERE slot_id = NEW.slot_id AND status = 'AVAILABLE';
        IF NOT FOUND THEN
            RAISE EXCEPTION 'Slot not available for booking';
        END IF;
    ELSIF NEW.status = 'CANCELLED' AND OLD.status = 'CONFIRMED' THEN
        UPDATE time_slots SET status = 'AVAILABLE', version = version + 1
        WHERE slot_id = NEW.slot_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_booking_slot_consistency
    AFTER INSERT OR UPDATE ON bookings
    FOR EACH ROW
    EXECUTE FUNCTION check_slot_booking_consistency();

-- Migration: Split 'name' into 'firstName'/'lastName' for users and ticket_orders
-- Run this BEFORE deploying the new backend version

-- =============================================================================
-- 1. USERS TABLE: name -> firstName + lastName
-- =============================================================================

-- Add new columns as NULLABLE first
ALTER TABLE users ADD COLUMN IF NOT EXISTS firstname VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS lastname VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone VARCHAR(20);

-- Migrate existing data: split 'name' into first/last
-- If name has a space, split on first space. Otherwise, put full name in firstName.
UPDATE users
SET firstname = CASE
        WHEN name LIKE '% %' THEN TRIM(SUBSTRING(name FROM 1 FOR POSITION(' ' IN name) - 1))
        ELSE TRIM(name)
    END,
    lastname = CASE
        WHEN name LIKE '% %' THEN TRIM(SUBSTRING(name FROM POSITION(' ' IN name) + 1))
        ELSE ''
    END
WHERE firstname IS NULL AND name IS NOT NULL;

-- Handle any remaining NULLs (shouldn't happen, but just in case)
UPDATE users SET firstname = 'Onbekend' WHERE firstname IS NULL OR firstname = '';
UPDATE users SET lastname = 'Onbekend' WHERE lastname IS NULL OR lastname = '';

-- Now make them NOT NULL
ALTER TABLE users ALTER COLUMN firstname SET NOT NULL;
ALTER TABLE users ALTER COLUMN lastname SET NOT NULL;

-- Drop the old column
ALTER TABLE users DROP COLUMN IF EXISTS name;

-- =============================================================================
-- 2. TICKET_ORDERS TABLE: buyerName -> buyerFirstName + buyerLastName
-- =============================================================================

-- Add new columns as NULLABLE first
ALTER TABLE ticket_orders ADD COLUMN IF NOT EXISTS buyerfirstname VARCHAR(100);
ALTER TABLE ticket_orders ADD COLUMN IF NOT EXISTS buyerlastname VARCHAR(100);

-- Migrate existing data
UPDATE ticket_orders
SET buyerfirstname = CASE
        WHEN buyername LIKE '% %' THEN TRIM(SUBSTRING(buyername FROM 1 FOR POSITION(' ' IN buyername) - 1))
        ELSE TRIM(buyername)
    END,
    buyerlastname = CASE
        WHEN buyername LIKE '% %' THEN TRIM(SUBSTRING(buyername FROM POSITION(' ' IN buyername) + 1))
        ELSE ''
    END
WHERE buyerfirstname IS NULL AND buyername IS NOT NULL;

-- Handle any remaining NULLs
UPDATE ticket_orders SET buyerfirstname = 'Onbekend' WHERE buyerfirstname IS NULL OR buyerfirstname = '';
UPDATE ticket_orders SET buyerlastname = 'Onbekend' WHERE buyerlastname IS NULL OR buyerlastname = '';

-- Now make them NOT NULL
ALTER TABLE ticket_orders ALTER COLUMN buyerfirstname SET NOT NULL;
ALTER TABLE ticket_orders ALTER COLUMN buyerlastname SET NOT NULL;

-- Drop the old column
ALTER TABLE ticket_orders DROP COLUMN IF EXISTS buyername;

-- =============================================================================
-- 3. Add address columns for ticket_orders (nullable, Hibernate update handles these
--    but including for completeness)
-- =============================================================================
ALTER TABLE ticket_orders ADD COLUMN IF NOT EXISTS buyerstreet VARCHAR(200);
ALTER TABLE ticket_orders ADD COLUMN IF NOT EXISTS buyerhousenumber VARCHAR(10);
ALTER TABLE ticket_orders ADD COLUMN IF NOT EXISTS buyerpostalcode VARCHAR(10);
ALTER TABLE ticket_orders ADD COLUMN IF NOT EXISTS buyercity VARCHAR(100);

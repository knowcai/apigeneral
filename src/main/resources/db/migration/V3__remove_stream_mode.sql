DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'api_version' AND column_name = 'response_mode'
    ) THEN
        UPDATE api_version SET response_mode = 'PAGE' WHERE response_mode = 'STREAM';
    END IF;
END $$;

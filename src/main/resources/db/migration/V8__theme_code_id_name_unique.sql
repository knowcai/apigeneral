-- 主题编码统一为自增 ID 字符串，名称唯一
UPDATE theme SET code = id::text;

UPDATE api_definition d
SET theme = t.code
FROM theme t
WHERE d.theme_id = t.id;

DO $$
BEGIN
    ALTER TABLE theme ADD CONSTRAINT uq_theme_name UNIQUE (name);
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

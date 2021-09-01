CREATE TABLE IF NOT EXISTS tags (
  image_id INT NOT NULL,
  value TEXT NOT NULL
);

CREATE UNIQUE INDEX tags_image_id_value_uidx
  ON tags(value, image_id);

CREATE INDEX tags_image_id_idx
  ON tags(image_id);

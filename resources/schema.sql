CREATE EXTENSION pgcrypto;
CREATE TABLE users (
  id SERIAL,
  name VARCHAR(256) UNIQUE,
  password VARCHAR(256),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY(id)
);

CREATE TABLE customers (
  id SERIAL,
  name VARCHAR(512) UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deleted BOOLEAN,
  user_id INT,
  PRIMARY KEY(id),
  CONSTRAINT fk_users FOREIGN KEY(user_id) REFERENCES users(id)
);

CREATE TABLE products (
  id SERIAL,
  name VARCHAR(512) UNIQUE,
  amount NUMERIC,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deleted BOOLEAN,
  user_id INT,
  PRIMARY KEY(id),
  CONSTRAINT fk_users FOREIGN KEY(user_id) REFERENCES users(id)
);

CREATE TYPE order_state AS ENUM('waiting', 'fulfilled', 'canceled');
CREATE TABLE orders (
  id SERIAL,
  customer_id INT,
  notes TEXT,
  status order_state DEFAULT 'waiting',
  order_date TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  user_id INT,
  PRIMARY KEY(id),
  CONSTRAINT fk_customer FOREIGN KEY(customer_id) REFERENCES customers(id),
  CONSTRAINT fk_users FOREIGN KEY(user_id) REFERENCES users(id)
);

CREATE TABLE order_products (
  id SERIAL,
  order_id INT,
  product_id INT,
  amount NUMERIC,
  PRIMARY KEY(id),
  CONSTRAINT fk_order FOREIGN KEY(order_id) REFERENCES orders(id),
  CONSTRAINT fk_product FOREIGN KEY(product_id) REFERENCES products(id)
);

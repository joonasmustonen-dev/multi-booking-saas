CREATE TABLE customers (
    id UUID PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(320),
    phone VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
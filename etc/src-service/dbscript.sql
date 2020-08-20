CREATE TABLE users (
	id varchar(50) NOT NULL,
	username varchar(50) NOT NULL,
	firstname varchar(50),
	lastname varchar(50),
	email varchar(50),
	workemail varchar(50),
	address varchar(50),
	department varchar(50),
	phonenumbers varchar(50),
	roles varchar(1000),
	CONSTRAINT PK_users PRIMARY KEY (id)
);

CREATE TABLE roles (
	id varchar(50) NOT NULL,
	label varchar(50) NOT NULL,
	CONSTRAINT PK_roles PRIMARY KEY (id)
);

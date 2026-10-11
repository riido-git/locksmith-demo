-- Safe to run more than once: three instances can share one database.
create table if not exists counter (
	name varchar(100) primary key,
	counter_value bigint not null
);

create table if not exists product (
	id bigint auto_increment primary key,
	sku varchar(50) not null unique,
	name varchar(100) not null,
	price int not null
);

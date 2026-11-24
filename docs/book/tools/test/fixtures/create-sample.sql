
    create schema g1_v4_bidi_mappedby;

    create sequence g1_v4_bidi_mappedby.customer_seq start with 1 increment by 50;

    create table g1_v4_bidi_mappedby.customer (
        id bigint not null,
        name varchar(255) not null,
        primary key (id)
    );

    create table g1_v4_bidi_mappedby.purchase_order (
        customer_id bigint,
        id bigint not null,
        order_no varchar(255) not null,
        primary key (id)
    );

    create table g2_c8_ondelete.purchase_order (
        id bigint not null,
        order_no varchar(255) not null,
        primary key (id)
    );

    create table g2_c8_ondelete.order_item (
        quantity integer not null,
        id bigint not null,
        order_id bigint,
        primary key (id)
    );

    create table g8_j1_explicit.warehouse (
        code varchar(255) not null,
        country_code varchar(255) not null,
        name varchar(255),
        primary key (code, country_code)
    );

    create table g8_j1_explicit.shipment (
        id bigint not null,
        wh_code varchar(255),
        wh_country varchar(255),
        primary key (id)
    );

    create table g6_v2_fk_on_card.customer (
        id bigint not null,
        name varchar(255) not null,
        primary key (id)
    );

    create table g6_v2_fk_on_card.loyalty_card (
        customer_id bigint unique,
        id bigint not null,
        card_no varchar(255) not null,
        primary key (id)
    );

    create table g9_s01_tree.category (
        id bigint not null,
        parent_id bigint,
        name varchar(255) not null,
        primary key (id)
    );

    create table g10_q1_baseline.purchase_order (
        id bigint not null,
        status varchar(255) not null check (status in ('NEW','PAID')),
        primary key (id)
    );

    alter table if exists g1_v4_bidi_mappedby.purchase_order
       add constraint FK158vbkwgyf5r6ogk9nkugqv2c
       foreign key (customer_id)
       references g1_v4_bidi_mappedby.customer;

    alter table if exists g2_c8_ondelete.order_item
       add constraint FKlbn1xgrugcgx68cetsqa6f7px
       foreign key (order_id)
       references g2_c8_ondelete.purchase_order
       on delete cascade;

    alter table if exists g8_j1_explicit.shipment
       add constraint fk_shipment_wh
       foreign key (wh_country, wh_code)
       references g8_j1_explicit.warehouse (country_code, code);

    alter table if exists g6_v2_fk_on_card.loyalty_card
       add constraint FKcard_customer
       foreign key (customer_id)
       references g6_v2_fk_on_card.customer;

    alter table if exists g9_s01_tree.category
       add constraint FKcategory_parent
       foreign key (parent_id)
       references g9_s01_tree.category;

-- 테스트 전용 엔티티(support/AuditingTestEntity) 테이블. SchemaMigrationTest에서만 운영 마이그레이션 뒤에 붙는다
create table auditing_test_entity (
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    updated_at datetime(6) not null,
    name varchar(50) not null,
    primary key (id)
) engine=InnoDB;

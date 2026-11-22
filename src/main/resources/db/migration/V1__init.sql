-- moyeo 초기 스키마(Task 067, 2026-11-22)
-- Flyway 도입 시점의 JPA 엔티티 기준 베이스라인. Hibernate 스키마 생성 결과를 그대로 옮겼다
-- 이미 적용된 마이그레이션 파일은 고치지 않는다. 스키마를 바꾸려면 V{n}__설명.sql을 새로 추가한다
-- 문자셋·시간대는 DB 기본값(utf8mb4, +09:00)을 따른다 — deploy/docker-compose.yml 참고

create table attendance (
    checked_in bit not null,
    no_show bit not null,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    member_id bigint not null,
    responded_at datetime(6) not null,
    round_id bigint not null,
    updated_at datetime(6) not null,
    status enum ('ABSENT','ATTEND','UNDECIDED') not null,
    primary key (id)
) engine=InnoDB;

create table attendance_reminder_push (
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    last_sent_at datetime(6) not null,
    last_sent_by_id bigint not null,
    meeting_id bigint not null,
    updated_at datetime(6) not null,
    primary key (id)
) engine=InnoDB;

create table board_game_rank (
    display_order integer not null check ((display_order>=0)),
    rank_no integer not null,
    board_game_record_id bigint not null,
    member_id bigint not null,
    primary key (display_order, board_game_record_id)
) engine=InnoDB;

create table board_game_record (
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    loser_member_id bigint,
    recorded_by bigint not null,
    round_id bigint not null,
    updated_at datetime(6) not null,
    version bigint not null,
    game_name varchar(50) not null,
    primary key (id)
) engine=InnoDB;

create table change_log (
    actor_id bigint,
    changed_at datetime(6) not null,
    entity_id bigint not null,
    id bigint not null auto_increment,
    meeting_id bigint not null,
    after_json TEXT,
    before_json TEXT,
    change_type enum ('CREATE','DELETE','UPDATE') not null,
    entity_type enum ('ATTENDANCE','EXPENSE','EXPENSE_SHARE','GAME_SESSION','PENALTY','PENALTY_RULE') not null,
    settlement_status_after enum ('CONFIRMED','DRAFT','REOPENED') not null,
    primary key (id)
) engine=InnoDB;

create table expense (
    amount bigint not null,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    payer_id bigint not null,
    round_id bigint not null,
    updated_at datetime(6) not null,
    memo varchar(100),
    primary key (id)
) engine=InnoDB;

create table expense_receipt (
    created_at datetime(6) not null,
    expense_id bigint not null,
    id bigint not null auto_increment,
    size bigint not null,
    updated_at datetime(6) not null,
    uploaded_at DATETIME(6) not null,
    uploaded_by bigint not null,
    version bigint not null,
    storage_key varchar(64) not null,
    image_type enum ('JPEG','PNG','WEBP') not null,
    primary key (id)
) engine=InnoDB;

create table expense_share (
    excluded bit not null,
    weight integer,
    amount bigint,
    created_at datetime(6) not null,
    expense_id bigint not null,
    id bigint not null auto_increment,
    member_id bigint not null,
    updated_at datetime(6) not null,
    primary key (id)
) engine=InnoDB;

create table favorite_place (
    created_at datetime(6) not null,
    created_by_id bigint not null,
    group_id bigint not null,
    id bigint not null auto_increment,
    updated_at datetime(6) not null,
    label varchar(50) not null,
    manual_address varchar(200),
    manual_map_url varchar(2048),
    primary key (id)
) engine=InnoDB;

create table friend_group (
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    updated_at datetime(6) not null,
    name varchar(50) not null,
    primary key (id)
) engine=InnoDB;

create table game_participant (
    created_at datetime(6) not null,
    game_session_id bigint not null,
    id bigint not null auto_increment,
    member_id bigint not null,
    updated_at datetime(6) not null,
    primary key (id)
) engine=InnoDB;

create table game_result (
    order_no integer,
    created_at datetime(6) not null,
    game_session_id bigint not null,
    id bigint not null auto_increment,
    member_id bigint not null,
    updated_at datetime(6) not null,
    outcome varchar(50) not null,
    primary key (id)
) engine=InnoDB;

create table game_session (
    penalty_count integer not null,
    apply_order_at DATETIME(6),
    canceled_rerun_of_id bigint,
    created_at datetime(6) not null,
    created_by bigint not null,
    id bigint not null auto_increment,
    rerun_of_id bigint,
    revealed_at datetime(6),
    root_session_id bigint,
    round_id bigint not null,
    seed bigint,
    updated_at datetime(6) not null,
    version bigint not null,
    penalty_note varchar(100),
    result_data TEXT,
    penalty_kind enum ('ACTION','MONEY','NONE') not null,
    status enum ('CANCELED','CLOSED','PLAYING','REVEALED','WAITING') not null,
    type enum ('DRAW','LADDER','ORDER','ROULETTE') not null,
    primary key (id)
) engine=InnoDB;

create table group_member (
    created_at datetime(6) not null,
    group_id bigint not null,
    id bigint not null auto_increment,
    member_id bigint not null,
    updated_at datetime(6) not null,
    emoji varchar(16),
    nickname varchar(50) not null,
    role enum ('MEMBER','OWNER','TREASURER') not null,
    primary key (id)
) engine=InnoDB;

create table group_member_payment (
    created_at datetime(6) not null,
    group_member_id bigint not null,
    id bigint not null auto_increment,
    updated_at datetime(6) not null,
    version bigint not null,
    account_holder varchar(20),
    bank_name varchar(20),
    encrypted_account_number varchar(128),
    kakao_pay_link varchar(2048),
    toss_link varchar(2048),
    primary key (id)
) engine=InnoDB;

create table invite_link (
    created_at datetime(6) not null,
    expires_at datetime(6) not null,
    group_id bigint not null,
    id bigint not null auto_increment,
    revoked_at datetime(6),
    updated_at datetime(6) not null,
    token varchar(43) not null,
    primary key (id)
) engine=InnoDB;

create table meeting (
    created_at datetime(6) not null,
    group_id bigint not null,
    host_member_id bigint not null,
    id bigint not null auto_increment,
    response_deadline datetime(6),
    start_at datetime(6),
    updated_at datetime(6) not null,
    version bigint not null,
    share_token varchar(43) not null,
    title varchar(100) not null,
    memo varchar(1000),
    date_status enum ('CONFIRMED','VOTING') not null,
    primary key (id)
) engine=InnoDB;

create table meeting_round (
    seq integer not null,
    attendance_confirmed_at datetime(6),
    confirmed_candidate_id bigint,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    meeting_id bigint not null,
    place_vote_closed_at datetime(6),
    place_vote_deadline datetime(6),
    updated_at datetime(6) not null,
    version bigint not null,
    name varchar(30) not null,
    place_status enum ('CONFIRMED','UNDECIDED','VOTING') not null,
    primary key (id)
) engine=InnoDB;

create table meeting_date_candidate (
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    meeting_id bigint not null,
    start_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id)
) engine=InnoDB;

create table meeting_date_vote (
    available bit not null,
    candidate_id bigint not null,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    member_id bigint not null,
    updated_at datetime(6) not null,
    primary key (id)
) engine=InnoDB;

create table meeting_guest (
    created_at datetime(6) not null,
    created_by bigint not null,
    id bigint not null auto_increment,
    meeting_id bigint not null,
    member_id bigint not null,
    updated_at datetime(6) not null,
    primary key (id)
) engine=InnoDB;

create table member (
    guest bit not null,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    kakao_id bigint,
    updated_at datetime(6) not null,
    nickname varchar(50) not null,
    email varchar(100),
    profile_image_url varchar(2048),
    primary key (id)
) engine=InnoDB;

create table penalty (
    created_at datetime(6) not null,
    game_session_id bigint not null,
    id bigint not null auto_increment,
    member_id bigint not null,
    updated_at datetime(6) not null,
    version bigint not null,
    waived_at datetime(6),
    waived_by bigint,
    status enum ('APPLIED','ASSIGNED','VOIDED','WAIVED') not null,
    primary key (id)
) engine=InnoDB;

create table penalty_rule (
    ratio_pct integer,
    amount bigint,
    game_session_id bigint not null,
    id bigint not null auto_increment,
    set_by bigint not null,
    target_expense_id bigint not null,
    updated_at datetime(6) not null,
    version bigint not null,
    money_type enum ('FIXED_AMOUNT','FULL','RATIO') not null,
    primary key (id)
) engine=InnoDB;

create table penalty_charge (
    ratio_pct integer,
    amount bigint not null,
    fixed_amount bigint,
    id bigint not null auto_increment,
    penalty_id bigint not null,
    revision_id bigint not null,
    target_expense_amount bigint not null,
    target_expense_id bigint not null,
    target_expense_memo varchar(100),
    money_type enum ('FIXED_AMOUNT','FULL','RATIO') not null,
    primary key (id)
) engine=InnoDB;

create table place_roulette_candidate (
    draw_order integer not null check ((draw_order>=0)),
    candidate_id bigint not null,
    place_roulette_id bigint not null,
    primary key (draw_order, place_roulette_id)
) engine=InnoDB;

create table place_candidate (
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    proposed_by_id bigint not null,
    round_id bigint not null,
    updated_at datetime(6) not null,
    label varchar(50) not null,
    manual_address varchar(200),
    manual_map_url varchar(2048),
    primary key (id)
) engine=InnoDB;

create table place_roulette (
    id bigint not null auto_increment,
    round_id bigint not null,
    seed bigint not null,
    spun_at datetime(6) not null,
    spun_by_id bigint not null,
    winner_id bigint not null,
    primary key (id)
) engine=InnoDB;

create table place_vote (
    candidate_id bigint not null,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    member_id bigint not null,
    updated_at datetime(6) not null,
    primary key (id)
) engine=InnoDB;

create table push_mute (
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    member_id bigint not null,
    updated_at datetime(6) not null,
    type varchar(30) not null,
    primary key (id)
) engine=InnoDB;

create table push_subscription (
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    member_id bigint not null,
    registered_at datetime(6) not null,
    updated_at datetime(6) not null,
    auth varchar(50) not null,
    endpoint_hash varchar(64) not null,
    p256dh varchar(100) not null,
    endpoint varchar(2048) not null,
    primary key (id)
) engine=InnoDB;

create table refresh_token (
    created_at datetime(6) not null,
    expires_at datetime(6) not null,
    id bigint not null auto_increment,
    member_id bigint not null,
    updated_at datetime(6) not null,
    token_hash varchar(64) not null,
    primary key (id)
) engine=InnoDB;

create table revision_member_share (
    id bigint not null auto_increment,
    member_id bigint not null,
    net_balance bigint not null,
    revision_id bigint not null,
    share_amount bigint not null,
    primary key (id)
) engine=InnoDB;

create table settlement (
    current_revision_no integer,
    created_at datetime(6) not null,
    first_confirmed_at datetime(6),
    id bigint not null auto_increment,
    meeting_id bigint not null,
    updated_at datetime(6) not null,
    version bigint not null,
    status enum ('CONFIRMED','DRAFT','REOPENED') not null,
    primary key (id)
) engine=InnoDB;

create table settlement_revision (
    revision_no integer not null,
    warnings_acknowledged bit not null,
    confirmed_at datetime(6) not null,
    confirmed_by bigint not null,
    id bigint not null auto_increment,
    settlement_id bigint not null,
    primary key (id)
) engine=InnoDB;

create table transfer (
    amount bigint not null,
    from_member_id bigint not null,
    id bigint not null auto_increment,
    revision_id bigint not null,
    to_member_id bigint not null,
    primary key (id)
) engine=InnoDB;

alter table attendance 
   add constraint uk_attendance_round_id_member_id unique (round_id, member_id);

alter table attendance_reminder_push 
   add constraint UKnxgvr6woth1jv65sdd6y50c3g unique (meeting_id);

alter table expense_receipt 
   add constraint uk_expense_receipt_expense_id unique (expense_id);

alter table expense_receipt 
   add constraint uk_expense_receipt_storage_key unique (storage_key);

alter table expense_share 
   add constraint uk_expense_share_expense_id_member_id unique (expense_id, member_id);

alter table game_participant 
   add constraint uk_game_participant_game_session_id_member_id unique (game_session_id, member_id);

alter table game_session 
   add constraint uk_game_session_rerun_of_id unique (rerun_of_id);

alter table group_member 
   add constraint uk_group_member_group_id_member_id unique (group_id, member_id);

alter table group_member_payment 
   add constraint uk_group_member_payment_group_member_id unique (group_member_id);

alter table invite_link 
   add constraint UK7gfv6wlsev04ay1tevdqqvchb unique (token);

alter table meeting 
   add constraint uk_meeting_share_token unique (share_token);

create index idx_meeting_round_place_status_place_vote_deadline 
   on meeting_round (place_status, place_vote_deadline);

alter table meeting_round 
   add constraint uk_meeting_round_meeting_id_seq unique (meeting_id, seq);

alter table meeting_date_candidate 
   add constraint uk_meeting_date_candidate_meeting_id_start_at unique (meeting_id, start_at);

alter table meeting_date_vote 
   add constraint uk_meeting_date_vote_candidate_id_member_id unique (candidate_id, member_id);

alter table meeting_guest 
   add constraint uk_meeting_guest_member_id unique (member_id);

alter table member 
   add constraint UKtqi1nx9ul3nx7guxpqycuvgue unique (kakao_id);

alter table penalty 
   add constraint uk_penalty_game_session_id_member_id unique (game_session_id, member_id);

alter table penalty_rule 
   add constraint uk_penalty_rule_game_session_id unique (game_session_id);

alter table penalty_charge 
   add constraint uk_penalty_charge_revision_id_penalty_id unique (revision_id, penalty_id);

alter table place_vote 
   add constraint uk_place_vote_candidate_id_member_id unique (candidate_id, member_id);

alter table push_mute 
   add constraint UK1gwecemks8cde378u8k9bhych unique (member_id, type);

alter table push_subscription 
   add constraint UK2dcsa786daq4py4wn3094cr60 unique (endpoint_hash);

alter table refresh_token 
   add constraint UKkdj16cltjxdksuyiosdhliveg unique (token_hash);

alter table revision_member_share 
   add constraint uk_revision_member_share_revision_id_member_id unique (revision_id, member_id);

alter table settlement 
   add constraint uk_settlement_meeting_id unique (meeting_id);

alter table settlement_revision 
   add constraint uk_settlement_revision_settlement_id_revision_no unique (settlement_id, revision_no);

alter table attendance 
   add constraint FKslaf4mu3eu0gi72u4t9xcsxjd 
   foreign key (member_id) 
   references member (id);

alter table attendance 
   add constraint FKhe428umg9o4nvp1melxwp8sy3 
   foreign key (round_id) 
   references meeting_round (id);

alter table attendance_reminder_push 
   add constraint FKnapagrnw362pvupnemthev8ws 
   foreign key (last_sent_by_id) 
   references member (id);

alter table attendance_reminder_push 
   add constraint FKgeqgl362ke1oso4qh0h9pc8ia 
   foreign key (meeting_id) 
   references meeting (id);

alter table board_game_rank 
   add constraint FKjpoxm6d14130pw8rhy3s2paat 
   foreign key (board_game_record_id) 
   references board_game_record (id);

alter table board_game_record 
   add constraint FK6m32e41qnq2oa8kfr4of716i7 
   foreign key (round_id) 
   references meeting_round (id);

alter table change_log 
   add constraint FKpblmjhglmogfrju8jq93ltcwj 
   foreign key (meeting_id) 
   references meeting (id);

alter table expense 
   add constraint FK4giveag22wwmiymro0tta6lx1 
   foreign key (payer_id) 
   references member (id);

alter table expense 
   add constraint FKfyylm826cb7yhs7w7o9vaq2fn 
   foreign key (round_id) 
   references meeting_round (id);

alter table expense_receipt 
   add constraint FKh24podb3mt1yy0x2ym6im32nm 
   foreign key (expense_id) 
   references expense (id);

alter table expense_share 
   add constraint FKhj2o29locg1x24mba0rsibjab 
   foreign key (expense_id) 
   references expense (id);

alter table expense_share 
   add constraint FK3fxqy7ni2pksmkgq8j7d0nddg 
   foreign key (member_id) 
   references member (id);

alter table favorite_place 
   add constraint FKsdmspfutymxyqw4y6uel3adca 
   foreign key (created_by_id) 
   references member (id);

alter table favorite_place 
   add constraint FK6kkklmhvi80pfvb67gxpu7b0f 
   foreign key (group_id) 
   references friend_group (id);

alter table game_participant 
   add constraint FKi78ldd0pvebwri8f9mybp0g1r 
   foreign key (game_session_id) 
   references game_session (id);

alter table game_participant 
   add constraint FKricaegmeojpia2qwx839l4gub 
   foreign key (member_id) 
   references member (id);

alter table game_result 
   add constraint FKisadhhcf6d2u1tc8ofoejhfu2 
   foreign key (game_session_id) 
   references game_session (id);

alter table game_result 
   add constraint FKn4gsrvi3hf64ug5kultcmjy4x 
   foreign key (member_id) 
   references member (id);

alter table game_session 
   add constraint FKq0adhposhg9x0ebm6x1nghahs 
   foreign key (canceled_rerun_of_id) 
   references game_session (id);

alter table game_session 
   add constraint FKmqatfesul68wwdtpg1tpaa9l2 
   foreign key (rerun_of_id) 
   references game_session (id);

alter table game_session 
   add constraint FK8k4hibqrnls2faw4imlw3v85f 
   foreign key (round_id) 
   references meeting_round (id);

alter table group_member 
   add constraint FKrfnhju8qb5yaorqjtom16m6eg 
   foreign key (group_id) 
   references friend_group (id);

alter table group_member 
   add constraint FKeamf7nngsg582uxwqgde8o28x 
   foreign key (member_id) 
   references member (id);

alter table group_member_payment 
   add constraint FK5gwta3unxj49t5t8ccpxw7o42 
   foreign key (group_member_id) 
   references group_member (id);

alter table invite_link 
   add constraint FKm6u2g0grnxs7huwfxoo657dal 
   foreign key (group_id) 
   references friend_group (id);

alter table meeting 
   add constraint FKexf3hgst1ueacx74xost9fdbj 
   foreign key (group_id) 
   references friend_group (id);

alter table meeting_round 
   add constraint FKgsxbbextyud524kxsu4ahcu91 
   foreign key (confirmed_candidate_id) 
   references place_candidate (id);

alter table meeting_round 
   add constraint FKrx3ovarukxr226aipl9j78p5t 
   foreign key (meeting_id) 
   references meeting (id);

alter table meeting_date_candidate 
   add constraint FKaeyv3jp73b4nyv519qq7854mf 
   foreign key (meeting_id) 
   references meeting (id);

alter table meeting_date_vote 
   add constraint FKbfy0peagwfm6jn0mu3seypuuq 
   foreign key (candidate_id) 
   references meeting_date_candidate (id);

alter table meeting_date_vote 
   add constraint FKj97vytjy93yqe4dtk74d8ts9d 
   foreign key (member_id) 
   references member (id);

alter table meeting_guest 
   add constraint FKbv9ur567sk1o00cgbidu7n8ey 
   foreign key (meeting_id) 
   references meeting (id);

alter table meeting_guest 
   add constraint FKcobxn5biyo3vy5ovonk1vs98k 
   foreign key (member_id) 
   references member (id);

alter table penalty 
   add constraint FKcqk8hu1p72y5nggyqv3dq8bjh 
   foreign key (game_session_id) 
   references game_session (id);

alter table penalty 
   add constraint FKeyfbl921ll65w4qm1g01bt2p6 
   foreign key (member_id) 
   references member (id);

alter table penalty_rule 
   add constraint FKe0fihwxf7cj8j6017icvsn4qq 
   foreign key (game_session_id) 
   references game_session (id);

alter table penalty_rule 
   add constraint FK8jh0w5ahn1dcbboqrf3kubcsm 
   foreign key (target_expense_id) 
   references expense (id);

alter table penalty_charge 
   add constraint FK5229rpmagbw13m519g2t17fo7 
   foreign key (penalty_id) 
   references penalty (id);

alter table penalty_charge 
   add constraint FK43pg1awm8ql2qbqkdfe0joei8 
   foreign key (revision_id) 
   references settlement_revision (id);

alter table place_roulette_candidate 
   add constraint FKjgkqb2bxnthdubwitcph9x8yl 
   foreign key (place_roulette_id) 
   references place_roulette (id);

alter table place_candidate 
   add constraint FK9tle9wceqmsqs2fubh89ow57g 
   foreign key (proposed_by_id) 
   references member (id);

alter table place_candidate 
   add constraint FK47qgduloqg896hqf5wmoi9u63 
   foreign key (round_id) 
   references meeting_round (id);

alter table place_roulette 
   add constraint FKbkn461mn2nwkblrbtkihc9i0m 
   foreign key (round_id) 
   references meeting_round (id);

alter table place_roulette 
   add constraint FKmp2mylxhrprwoi3ohhwaiuh4k 
   foreign key (spun_by_id) 
   references member (id);

alter table place_roulette 
   add constraint FKnw9ie8n2a5cool1gl1nruw9dn 
   foreign key (winner_id) 
   references place_candidate (id);

alter table place_vote 
   add constraint FK2qnp6ldy80jjraummmdtaf1n2 
   foreign key (candidate_id) 
   references place_candidate (id);

alter table place_vote 
   add constraint FK6g4q7asaofmii57efom512ifj 
   foreign key (member_id) 
   references member (id);

alter table push_mute 
   add constraint FK4thiynaocr4igtbulmnxd1txu 
   foreign key (member_id) 
   references member (id);

alter table push_subscription 
   add constraint FK3a7hecut4yr9j32qytv1yf2ei 
   foreign key (member_id) 
   references member (id);

alter table refresh_token 
   add constraint FK5gdbafb2i76hk1ai18ah6an4w 
   foreign key (member_id) 
   references member (id);

alter table revision_member_share 
   add constraint FKdicl7b70ekdh46i1glmb0yhpx 
   foreign key (member_id) 
   references member (id);

alter table revision_member_share 
   add constraint FKk18w1pwda54nha7c9ojv6sgx9 
   foreign key (revision_id) 
   references settlement_revision (id);

alter table settlement 
   add constraint FKeijt4j9l628fg5wmwunfylsxm 
   foreign key (meeting_id) 
   references meeting (id);

alter table settlement_revision 
   add constraint FKdg38v9ae13cdjhcfa9w5cayyj 
   foreign key (confirmed_by) 
   references member (id);

alter table settlement_revision 
   add constraint FKnynn2xw1vcpyst8s3qerrokux 
   foreign key (settlement_id) 
   references settlement (id);

alter table transfer 
   add constraint FK2qmcj44bo62ly80k0raufhljb 
   foreign key (from_member_id) 
   references member (id);

alter table transfer 
   add constraint FK7ckina0lt0tkmnagxo16ui8ou 
   foreign key (revision_id) 
   references settlement_revision (id);

alter table transfer 
   add constraint FKicu9wnqgx6mxkvipb9t7eirxm 
   foreign key (to_member_id) 
   references member (id);

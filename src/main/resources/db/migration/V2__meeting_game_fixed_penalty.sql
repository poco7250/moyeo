-- Task 074(2026-11-24): 게임을 차수 대신 모임에 묶고, 금액형 벌칙 금액을 게임을 만들 때 정한다.
-- 게임 후 지출을 골라 고정·비율·전액을 정하던 금액 조건(penalty_rule, R-9)은 없앤다.

-- 1) 게임 → 모임: 차수의 모임으로 채운 뒤 차수 FK·컬럼을 지운다
alter table game_session add column meeting_id bigint;

update game_session g
    join meeting_round r on r.id = g.round_id
set g.meeting_id = r.meeting_id;

alter table game_session modify meeting_id bigint not null;

alter table game_session
   add constraint fk_game_session_meeting_id
   foreign key (meeting_id)
   references meeting (id);

alter table game_session drop foreign key FK8k4hibqrnls2faw4imlw3v85f;

alter table game_session drop column round_id;

-- 2) 금액형 벌칙 1인 금액
alter table game_session add column penalty_amount bigint;

-- 고정 금액 조건이 있던 게임은 그 금액을 게임 금액으로 옮기고, 조건 미설정이던 벌칙을 정산 반영(APPLIED)으로 바꾼다
update game_session g
    join penalty_rule pr on pr.game_session_id = g.id
set g.penalty_amount = pr.amount
where pr.money_type = 'FIXED_AMOUNT';

update penalty p
    join game_session g on g.id = p.game_session_id
set p.status = 'APPLIED'
where g.penalty_amount is not null
  and p.status = 'ASSIGNED';

-- 비율·전액 조건이거나 조건이 없던 금액형 게임은 금액을 옮길 수 없어, 비무효 벌칙을 면제(WAIVED)로 둔다
update penalty p
    join game_session g on g.id = p.game_session_id
set p.status = 'WAIVED'
where g.penalty_kind = 'MONEY'
  and g.penalty_amount is null
  and p.status in ('ASSIGNED', 'APPLIED');

drop table penalty_rule;

-- 3) 리비전 벌칙 스냅샷: 새 스냅샷은 대상 지출이 없다(지난 리비전 값은 그대로 둔다)
alter table penalty_charge modify target_expense_id bigint null;
alter table penalty_charge modify target_expense_amount bigint null;

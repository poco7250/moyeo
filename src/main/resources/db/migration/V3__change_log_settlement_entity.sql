-- Task 075(2026-11-25): 호스트가 정산 확정을 되돌린 기록을 변경 로그에 남기려고 대상 종류에 SETTLEMENT를 추가한다.
alter table change_log
    modify entity_type enum ('ATTENDANCE','EXPENSE','EXPENSE_SHARE','GAME_SESSION','PENALTY','PENALTY_RULE','SETTLEMENT') not null;

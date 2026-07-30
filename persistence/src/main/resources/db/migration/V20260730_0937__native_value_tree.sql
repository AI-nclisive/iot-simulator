alter table value_timeline drop constraint if exists value_timeline_value_kind_check;
alter table value_timeline add constraint value_timeline_value_kind_check
    check (value_kind in ('NUM', 'INT', 'BOOL', 'TEXT', 'BYTES', 'TREE'));

alter table run_value_timeline drop constraint if exists run_value_timeline_value_kind_check;
alter table run_value_timeline add constraint run_value_timeline_value_kind_check
    check (value_kind in ('NUM', 'INT', 'BOOL', 'TEXT', 'BYTES', 'TREE'));

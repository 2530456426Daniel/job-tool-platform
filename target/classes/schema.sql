-- 共创会人员参会情况统计相关表结构。
--
-- 说明：
--   1. 当前使用 H2 内存库（MODE=MySQL），应用每次启动都会执行本脚本；
--      因此统一使用 IF NOT EXISTS，避免重复建表报错。
--   2. 切换到 MySQL 时，去掉 auto_increment 之外的兼容写法即可（索引语句改为
--      MySQL 语法或在建表语句内声明），业务代码无需改动。
--   3. meeting_activity 只用于回显/导出「共创会名称 / 活动日期」，没有数据也能正常统计，
--      需要时手工插入一行即可，例如：
--      insert into meeting_activity (id, meeting_name, meeting_begin_time)
--      values (645, '集团7月共创会', timestamp '2026-07-02 14:00:00');

create table if not exists meeting_activity
(
    id                 bigint auto_increment primary key,
    meeting_name       varchar(255),
    meeting_city_code  varchar(64),
    meeting_city_name  varchar(128),
    meeting_begin_time timestamp,
    meeting_end_time   timestamp
);

create table if not exists meeting_user_real_record
(
    id                 bigint auto_increment primary key,
    activity_id        bigint       not null,
    team_id            bigint       not null,
    team_name          varchar(255),
    city_code          varchar(64),
    city_name          varchar(128),
    dept_level_one     varchar(64),
    dept_level_two     varchar(64),
    dept_level_three   varchar(64),
    dept_level_four    varchar(64),
    dept_level_five    varchar(64),
    dept_level_six     varchar(64),
    dept_level_seven   varchar(64),
    user_code          bigint       not null,
    user_name          varchar(128),
    join_flag          int,
    join_time          timestamp,
    join_late_flag     int,
    watch_duration     bigint,
    answer_flag        int,
    answer_score_total double,
    create_time        timestamp
);

create index if not exists idx_meeting_record_activity_team
    on meeting_user_real_record (activity_id, team_id);

(ns cmr.bootstrap.migrations.001-create-quartz-tables
  (:require [config.bootstrap-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 1."
  []
  (println "migrations.001-create-quartz-tables up...")
  (h/sql "CREATE TABLE qrtz_job_details (
                              SCHED_NAME VARCHAR2(120) NOT NULL,
                              JOB_NAME  VARCHAR2(200) NOT NULL,
                              JOB_GROUP VARCHAR2(200) NOT NULL,
                              DESCRIPTION VARCHAR2(250) NULL,
                              JOB_CLASS_NAME   VARCHAR2(250) NOT NULL,
                              IS_DURABLE VARCHAR2(1) NOT NULL,
                              IS_NONCONCURRENT VARCHAR2(1) NOT NULL,
                              IS_UPDATE_DATA VARCHAR2(1) NOT NULL,
                              REQUESTS_RECOVERY VARCHAR2(1) NOT NULL,
                              JOB_DATA BLOB NULL,
                              CONSTRAINT QRTZ_JOB_DETAILS_PK PRIMARY KEY (SCHED_NAME,JOB_NAME,JOB_GROUP)
                              )")
  (h/sql "CREATE TABLE qrtz_triggers
                              (
                              SCHED_NAME VARCHAR2(120) NOT NULL,
                              TRIGGER_NAME VARCHAR2(200) NOT NULL,
                              TRIGGER_GROUP VARCHAR2(200) NOT NULL,
                              JOB_NAME  VARCHAR2(200) NOT NULL,
                              JOB_GROUP VARCHAR2(200) NOT NULL,
                              DESCRIPTION VARCHAR2(250) NULL,
                              NEXT_FIRE_TIME NUMBER(13) NULL,
                              PREV_FIRE_TIME NUMBER(13) NULL,
                              PRIORITY NUMBER(13) NULL,
                              TRIGGER_STATE VARCHAR2(16) NOT NULL,
                              TRIGGER_TYPE VARCHAR2(8) NOT NULL,
                              START_TIME NUMBER(13) NOT NULL,
                              END_TIME NUMBER(13) NULL,
                              CALENDAR_NAME VARCHAR2(200) NULL,
                              MISFIRE_INSTR NUMBER(2) NULL,
                              JOB_DATA BLOB NULL,
                              CONSTRAINT QRTZ_TRIGGERS_PK PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP),
                              CONSTRAINT QRTZ_TRIGGER_TO_JOBS_FK FOREIGN KEY (SCHED_NAME,JOB_NAME,JOB_GROUP)
                              REFERENCES QRTZ_JOB_DETAILS(SCHED_NAME,JOB_NAME,JOB_GROUP)
                              )")
  (h/sql "CREATE TABLE qrtz_simple_triggers
                              (
                              SCHED_NAME VARCHAR2(120) NOT NULL,
                              TRIGGER_NAME VARCHAR2(200) NOT NULL,
                              TRIGGER_GROUP VARCHAR2(200) NOT NULL,
                              REPEAT_COUNT NUMBER(7) NOT NULL,
                              REPEAT_INTERVAL NUMBER(12) NOT NULL,
                              TIMES_TRIGGERED NUMBER(10) NOT NULL,
                              CONSTRAINT QRTZ_SIMPLE_TRIG_PK PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP),
                              CONSTRAINT QRTZ_SIMPLE_TRIG_TO_TRIG_FK FOREIGN KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
                              REFERENCES QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
                              )")
  (h/sql "CREATE TABLE qrtz_cron_triggers
                              (
                              SCHED_NAME VARCHAR2(120) NOT NULL,
                              TRIGGER_NAME VARCHAR2(200) NOT NULL,
                              TRIGGER_GROUP VARCHAR2(200) NOT NULL,
                              CRON_EXPRESSION VARCHAR2(120) NOT NULL,
                              TIME_ZONE_ID VARCHAR2(80),
                              CONSTRAINT QRTZ_CRON_TRIG_PK PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP),
                              CONSTRAINT QRTZ_CRON_TRIG_TO_TRIG_FK FOREIGN KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
                              REFERENCES QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
                              )")
  (h/sql "CREATE TABLE qrtz_simprop_triggers
                              (
                              SCHED_NAME VARCHAR2(120) NOT NULL,
                              TRIGGER_NAME VARCHAR2(200) NOT NULL,
                              TRIGGER_GROUP VARCHAR2(200) NOT NULL,
                              STR_PROP_1 VARCHAR2(512) NULL,
                              STR_PROP_2 VARCHAR2(512) NULL,
                              STR_PROP_3 VARCHAR2(512) NULL,
                              INT_PROP_1 NUMBER(10) NULL,
                              INT_PROP_2 NUMBER(10) NULL,
                              LONG_PROP_1 NUMBER(13) NULL,
                              LONG_PROP_2 NUMBER(13) NULL,
                              DEC_PROP_1 NUMERIC(13,4) NULL,
                              DEC_PROP_2 NUMERIC(13,4) NULL,
                              BOOL_PROP_1 VARCHAR2(1) NULL,
                              BOOL_PROP_2 VARCHAR2(1) NULL,
                              CONSTRAINT QRTZ_SIMPROP_TRIG_PK PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP),
                              CONSTRAINT QRTZ_SIMPROP_TRIG_TO_TRIG_FK FOREIGN KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
                              REFERENCES QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
                              )")
  (h/sql "CREATE TABLE qrtz_blob_triggers
                              (
                              SCHED_NAME VARCHAR2(120) NOT NULL,
                              TRIGGER_NAME VARCHAR2(200) NOT NULL,
                              TRIGGER_GROUP VARCHAR2(200) NOT NULL,
                              BLOB_DATA BLOB NULL,
                              CONSTRAINT QRTZ_BLOB_TRIG_PK PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP),
                              CONSTRAINT QRTZ_BLOB_TRIG_TO_TRIG_FK FOREIGN KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
                              REFERENCES QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
                              )")
  (h/sql "CREATE TABLE qrtz_calendars
                              (
                              SCHED_NAME VARCHAR2(120) NOT NULL,
                              CALENDAR_NAME  VARCHAR2(200) NOT NULL,
                              CALENDAR BLOB NOT NULL,
                              CONSTRAINT QRTZ_CALENDARS_PK PRIMARY KEY (SCHED_NAME,CALENDAR_NAME)
                              )")
  (h/sql "CREATE TABLE qrtz_paused_trigger_grps
                              (
                              SCHED_NAME VARCHAR2(120) NOT NULL,
                              TRIGGER_GROUP  VARCHAR2(200) NOT NULL,
                              CONSTRAINT QRTZ_PAUSED_TRIG_GRPS_PK PRIMARY KEY (SCHED_NAME,TRIGGER_GROUP)
                              )")
  (h/sql "CREATE TABLE qrtz_fired_triggers
                              (
                              SCHED_NAME VARCHAR2(120) NOT NULL,
                              ENTRY_ID VARCHAR2(95) NOT NULL,
                              TRIGGER_NAME VARCHAR2(200) NOT NULL,
                              TRIGGER_GROUP VARCHAR2(200) NOT NULL,
                              INSTANCE_NAME VARCHAR2(200) NOT NULL,
                              FIRED_TIME NUMBER(13) NOT NULL,
                              PRIORITY NUMBER(13) NOT NULL,
                              STATE VARCHAR2(16) NOT NULL,
                              JOB_NAME VARCHAR2(200) NULL,
                              JOB_GROUP VARCHAR2(200) NULL,
                              IS_NONCONCURRENT VARCHAR2(1) NULL,
                              REQUESTS_RECOVERY VARCHAR2(1) NULL,
                              CONSTRAINT QRTZ_FIRED_TRIGGER_PK PRIMARY KEY (SCHED_NAME,ENTRY_ID)
                              )")

  (h/sql "CREATE TABLE qrtz_scheduler_state
                              (
                              SCHED_NAME VARCHAR2(120) NOT NULL,
                              INSTANCE_NAME VARCHAR2(200) NOT NULL,
                              LAST_CHECKIN_TIME NUMBER(13) NOT NULL,
                              CHECKIN_INTERVAL NUMBER(13) NOT NULL,
                              CONSTRAINT QRTZ_SCHEDULER_STATE_PK PRIMARY KEY (SCHED_NAME,INSTANCE_NAME)
                              )")
  (h/sql "CREATE TABLE qrtz_locks
                              (
                              SCHED_NAME VARCHAR2(120) NOT NULL,
                              LOCK_NAME  VARCHAR2(40) NOT NULL,
                              CONSTRAINT QRTZ_LOCKS_PK PRIMARY KEY (SCHED_NAME,LOCK_NAME)
                              )")

  (h/sql "CREATE INDEX idx_qrtz_j_req_recovery ON qrtz_job_details(SCHED_NAME,REQUESTS_RECOVERY)")
  (h/sql "CREATE INDEX idx_qrtz_j_grp ON qrtz_job_details(SCHED_NAME,JOB_GROUP)")

  (h/sql "CREATE INDEX idx_qrtz_t_j ON qrtz_triggers(SCHED_NAME,JOB_NAME,JOB_GROUP)")
  (h/sql "CREATE INDEX idx_qrtz_t_jg ON qrtz_triggers(SCHED_NAME,JOB_GROUP)")
  (h/sql "CREATE INDEX idx_qrtz_t_c ON qrtz_triggers(SCHED_NAME,CALENDAR_NAME)")
  (h/sql "CREATE INDEX idx_qrtz_t_g ON qrtz_triggers(SCHED_NAME,TRIGGER_GROUP)")
  (h/sql "CREATE INDEX idx_qrtz_t_state ON qrtz_triggers(SCHED_NAME,TRIGGER_STATE)")
  (h/sql "CREATE INDEX idx_qrtz_t_n_state ON qrtz_triggers(SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP,TRIGGER_STATE)")
  (h/sql "CREATE INDEX idx_qrtz_t_n_g_state ON qrtz_triggers(SCHED_NAME,TRIGGER_GROUP,TRIGGER_STATE)")
  (h/sql "CREATE INDEX idx_qrtz_t_next_fire_time ON qrtz_triggers(SCHED_NAME,NEXT_FIRE_TIME)")
  (h/sql "CREATE INDEX idx_qrtz_t_nft_st ON qrtz_triggers(SCHED_NAME,TRIGGER_STATE,NEXT_FIRE_TIME)")
  (h/sql "CREATE INDEX idx_qrtz_t_nft_misfire ON qrtz_triggers(SCHED_NAME,MISFIRE_INSTR,NEXT_FIRE_TIME)")
  (h/sql "CREATE INDEX idx_qrtz_t_nft_st_misfire ON qrtz_triggers(SCHED_NAME,MISFIRE_INSTR,NEXT_FIRE_TIME,TRIGGER_STATE)")
  (h/sql "CREATE INDEX idx_qrtz_t_nft_st_misfire_grp ON qrtz_triggers(SCHED_NAME,MISFIRE_INSTR,NEXT_FIRE_TIME,TRIGGER_GROUP,TRIGGER_STATE)")

  (h/sql "CREATE INDEX idx_qrtz_ft_trig_inst_name ON qrtz_fired_triggers(SCHED_NAME,INSTANCE_NAME)")
  (h/sql "CREATE INDEX idx_qrtz_ft_inst_job_req_rcvry ON qrtz_fired_triggers(SCHED_NAME,INSTANCE_NAME,REQUESTS_RECOVERY)")
  (h/sql "CREATE INDEX idx_qrtz_ft_j_g ON qrtz_fired_triggers(SCHED_NAME,JOB_NAME,JOB_GROUP)")
  (h/sql "CREATE INDEX idx_qrtz_ft_jg ON qrtz_fired_triggers(SCHED_NAME,JOB_GROUP)")
  (h/sql "CREATE INDEX idx_qrtz_ft_t_g ON qrtz_fired_triggers(SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)")
  (h/sql "CREATE INDEX idx_qrtz_ft_tg ON qrtz_fired_triggers(SCHED_NAME,TRIGGER_GROUP)"))

(defn down
  "Migrates the database down from version 1."
  []
  (println "migrations.001-create-quartz-tables down...")
  (h/sql "DROP TABLE qrtz_calendars")
  (h/sql "DROP TABLE qrtz_fired_triggers")
  (h/sql "DROP TABLE qrtz_blob_triggers")
  (h/sql "DROP TABLE qrtz_cron_triggers")
  (h/sql "DROP TABLE qrtz_simple_triggers")
  (h/sql "DROP TABLE qrtz_simprop_triggers")
  (h/sql "DROP TABLE qrtz_triggers")
  (h/sql "DROP TABLE qrtz_job_details")
  (h/sql "DROP TABLE qrtz_paused_trigger_grps")
  (h/sql "DROP TABLE qrtz_locks")
  (h/sql "DROP TABLE qrtz_scheduler_state"))

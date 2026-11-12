-- =====================================================================
--  RMS (Resource Management System)  -  full schema + dummy data
--  Database : rms_db   (MySQL 8+)
--  Login    : username = admin   password = admin@123   (BCrypt hash below)
--
--  HOW TO IMPORT
--    MySQL Workbench : File > Open SQL Script > run (lightning icon)
--    Command line    : mysql -u root -p < rms_db_full_dump.sql
--
--  WARNING: this script DROPs and re-creates every table, so run it only
--           on an empty / throw-away rms_db.
--  AFTER IMPORT: set  spring.jpa.hibernate.ddl-auto=update  (or none) in
--           application.properties. With "create" Hibernate drops all the
--           tables again at startup and your data is gone.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS rms_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE rms_db;

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS audit_logs;
DROP TABLE IF EXISTS skill_evidence;
DROP TABLE IF EXISTS learning_recommendations;
DROP TABLE IF EXISTS learning_resources;
DROP TABLE IF EXISTS skill_gaps;
DROP TABLE IF EXISTS allocation_decisions;
DROP TABLE IF EXISTS matching_results;
DROP TABLE IF EXISTS matching_runs;
DROP TABLE IF EXISTS employee_allocations;
DROP TABLE IF EXISTS required_skills;
DROP TABLE IF EXISTS project_requirements;
DROP TABLE IF EXISTS projects;
DROP TABLE IF EXISTS resource_availability;
DROP TABLE IF EXISTS employee_skills;
DROP TABLE IF EXISTS employees;
DROP TABLE IF EXISTS skills;
DROP TABLE IF EXISTS designations;
DROP TABLE IF EXISTS departments;
DROP TABLE IF EXISTS locations;
DROP TABLE IF EXISTS app_users;

SET FOREIGN_KEY_CHECKS = 1;

-- =====================================================================
--  1. SCHEMA
-- =====================================================================

CREATE TABLE app_users (
    user_id        INT          NOT NULL AUTO_INCREMENT,
    username       VARCHAR(255) NOT NULL,
    full_name      VARCHAR(255),
    email          VARCHAR(255),
    password_hash  VARCHAR(255) NOT NULL,
    role           VARCHAR(255),
    is_active      TINYINT(1)   NOT NULL DEFAULT 1,       -- User has @SQLRestriction("is_active=1")
    created_at     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_app_users_username (username)
) ENGINE=InnoDB;

CREATE TABLE locations (
    location_id    INT          NOT NULL AUTO_INCREMENT,
    location_name  VARCHAR(255),
    city           VARCHAR(255),
    state_name     VARCHAR(255),
    country_name   VARCHAR(255),
    is_active      TINYINT(1)   DEFAULT 1,
    PRIMARY KEY (location_id)
) ENGINE=InnoDB;

CREATE TABLE departments (
    department_id    INT          NOT NULL AUTO_INCREMENT,
    department_name  VARCHAR(255),
    is_active        TINYINT(1)   NOT NULL DEFAULT 1,     -- Departments has @SQLRestriction("is_active = 1")
    PRIMARY KEY (department_id)
) ENGINE=InnoDB;

CREATE TABLE designations (
    designation_id    BIGINT       NOT NULL AUTO_INCREMENT,
    designation_name  VARCHAR(255),
    PRIMARY KEY (designation_id)
) ENGINE=InnoDB;

CREATE TABLE skills (
    skill_id        INT          NOT NULL AUTO_INCREMENT,
    skill_name      VARCHAR(255),
    skill_category  VARCHAR(255),
    PRIMARY KEY (skill_id)
) ENGINE=InnoDB;

CREATE TABLE employees (
    employee_id             INT          NOT NULL AUTO_INCREMENT,
    employee_code           VARCHAR(255),
    employee_name           VARCHAR(255),
    email                   VARCHAR(255),
    phone                   VARCHAR(255),
    total_experience_years  DOUBLE,
    designation_id          BIGINT,
    department_id           INT,
    location_id             INT,
    date_of_joining         DATE,
    employment_status       VARCHAR(255),
    source_system           VARCHAR(255),
    erp_employee_id         VARCHAR(255),
    erp_last_synced_at      DATETIME,
    created_at              DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (employee_id),
    CONSTRAINT fk_emp_designation FOREIGN KEY (designation_id) REFERENCES designations (designation_id),
    CONSTRAINT fk_emp_department  FOREIGN KEY (department_id)  REFERENCES departments (department_id),
    CONSTRAINT fk_emp_location    FOREIGN KEY (location_id)    REFERENCES locations (location_id)
) ENGINE=InnoDB;

CREATE TABLE employee_skills (
    employee_skill_id     INT          NOT NULL AUTO_INCREMENT,
    employee_id           INT,
    skill_id              INT,
    is_active             INT          DEFAULT 1,
    skill_priority        VARCHAR(255),
    proficiency_level     VARCHAR(255),                   -- stored as text "1".."5"
    experience_months     INT,
    validation_status     VARCHAR(255),
    information_source    VARCHAR(255),
    last_skill_updated_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
    created_at            DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (employee_skill_id),
    CONSTRAINT fk_es_employee FOREIGN KEY (employee_id) REFERENCES employees (employee_id),
    CONSTRAINT fk_es_skill    FOREIGN KEY (skill_id)    REFERENCES skills (skill_id)
) ENGINE=InnoDB;

CREATE TABLE resource_availability (
    availability_id                INT          NOT NULL AUTO_INCREMENT,
    employee_id                    INT,
    availability_status            VARCHAR(255),
    available_from                 DATE,
    available_until                DATE,
    available_capacity_percentage  DECIMAL(5,2),
    expected_duration_months       INT,
    availability_notes             VARCHAR(255),
    effective_from                 DATE,
    effective_until                DATE,
    recorded_by                    INT,
    created_at                     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (availability_id),
    CONSTRAINT fk_ra_employee FOREIGN KEY (employee_id) REFERENCES employees (employee_id)
) ENGINE=InnoDB;

CREATE TABLE projects (
    project_id           INT          NOT NULL AUTO_INCREMENT,
    project_code         VARCHAR(255),
    project_name         VARCHAR(255),
    project_domain       VARCHAR(255),
    project_description  TEXT,
    project_status       VARCHAR(255) DEFAULT 'DRAFT',
    start_date           DATE,
    end_date             DATE,
    created_by           INT,
    created_at           DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (project_id)
) ENGINE=InnoDB;

CREATE TABLE project_requirements (
    requirement_id             INT          NOT NULL AUTO_INCREMENT,
    project_id                 INT,
    requirement_code           VARCHAR(255),
    role_name                  VARCHAR(255),
    role_description           TEXT,
    required_headcount         INT          DEFAULT 1,
    minimum_proficiency_level  INT          DEFAULT 3,
    minimum_experience_months  INT          DEFAULT 0,
    joining_date               DATE,
    availability_within_days   INT          DEFAULT 15,
    project_duration_months    INT,
    location                   VARCHAR(255),
    work_mode                  VARCHAR(255) DEFAULT 'AS_REQUIRED',
    requirement_priority       VARCHAR(255) DEFAULT 'MEDIUM',
    requirement_owner          INT,
    requirement_status         VARCHAR(255) DEFAULT 'DRAFT',
    created_at                 DATETIME     DEFAULT CURRENT_TIMESTAMP,
    ip_address                 VARCHAR(255),
    PRIMARY KEY (requirement_id)
) ENGINE=InnoDB;

CREATE TABLE required_skills (
    required_skill_id    INT        NOT NULL AUTO_INCREMENT,
    requirement_id       INT,
    skill_id             INT,
    is_mandatory         TINYINT(1),
    minimum_proficiency  INT,
    created_at           DATETIME   DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (required_skill_id)
) ENGINE=InnoDB;

CREATE TABLE employee_allocations (
    allocation_id          INT          NOT NULL AUTO_INCREMENT,
    employee_id            INT,
    requirement_id         INT,
    allocation_percentage  DECIMAL(5,2) DEFAULT 100,
    allocation_status      VARCHAR(255) DEFAULT 'PLANNED',
    start_date             DATE,
    end_date               DATE,
    created_by             INT,
    created_at             DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at             DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (allocation_id)
) ENGINE=InnoDB;

CREATE TABLE matching_runs (
    matching_run_id                INT          NOT NULL AUTO_INCREMENT,
    requirement_id                 INT,
    execution_date                 DATETIME     DEFAULT CURRENT_TIMESTAMP,
    scoring_configuration_version  VARCHAR(255),
    algorithm_name                 VARCHAR(255),
    algorithm_version              VARCHAR(255),
    model_version                  VARCHAR(255),
    candidate_pool_size            INT          DEFAULT 0,
    run_status                     VARCHAR(255) DEFAULT 'QUEUED',
    executed_by                    INT,
    completed_at                   DATETIME,
    error_message                  TEXT,
    previous_run_id                INT,
    run_trigger                    VARCHAR(255) DEFAULT 'NEW_SEARCH',
    rematch_note                   VARCHAR(255),
    PRIMARY KEY (matching_run_id)
) ENGINE=InnoDB;

CREATE TABLE matching_results (
    matching_result_id     INT          NOT NULL AUTO_INCREMENT,
    matching_run_id        INT,
    employee_id            INT,
    overall_score          DECIMAL(6,2),
    match_category         VARCHAR(255),
    strengths              JSON,
    skill_gaps             JSON,
    explanation            TEXT,
    rank_position          INT,
    recommendation_status  VARCHAR(255) DEFAULT 'REVIEW_REQUIRED',
    created_at             DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (matching_result_id)
) ENGINE=InnoDB;

CREATE TABLE allocation_decisions (
    allocation_decision_id  INT          NOT NULL AUTO_INCREMENT,
    matching_result_id      INT,
    requirement_id          INT,
    allocation_id           INT,
    decision                VARCHAR(255),
    decision_reason         TEXT,
    decision_maker          INT,
    decision_date           DATETIME     DEFAULT CURRENT_TIMESTAMP,
    created_at              DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (allocation_decision_id)
) ENGINE=InnoDB;

CREATE TABLE skill_gaps (
    skill_gap_id          INT          NOT NULL AUTO_INCREMENT,
    matching_result_id    INT,
    skill_id              INT,
    gap_type              VARCHAR(255),
    is_mandatory          TINYINT(1)   DEFAULT 0,
    required_proficiency  INT,
    current_proficiency   INT,
    gap_size              INT,
    priority              VARCHAR(255) DEFAULT 'MEDIUM',
    priority_reason       VARCHAR(255),
    created_at            DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (skill_gap_id)
) ENGINE=InnoDB;

CREATE TABLE learning_resources (
    learning_resource_id  INT           NOT NULL AUTO_INCREMENT,
    skill_id              INT,
    resource_title        VARCHAR(255),
    resource_type         VARCHAR(255)  DEFAULT 'COURSE',
    provider              VARCHAR(255),
    resource_url          VARCHAR(255),
    resource_description  TEXT,
    from_proficiency      INT           DEFAULT 1,
    to_proficiency        INT,
    estimated_hours       DECIMAL(6,2),
    validation_method     VARCHAR(255)  DEFAULT 'KNOWLEDGE_ASSESSMENT',
    is_approved           TINYINT(1)    DEFAULT 0,
    approved_by           INT,
    approved_at           DATETIME,
    is_active             TINYINT(1)    DEFAULT 1,
    created_by            INT,
    created_at            DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (learning_resource_id)
) ENGINE=InnoDB;

CREATE TABLE learning_recommendations (
    learning_recommendation_id  INT           NOT NULL AUTO_INCREMENT,
    matching_result_id          INT,
    skill_id                    INT,
    current_proficiency         INT,
    target_proficiency          INT,
    skill_gap_description       TEXT,
    learning_action             VARCHAR(255),
    learning_resource           VARCHAR(255),
    resource_type               VARCHAR(255)  DEFAULT 'COURSE',
    review_status               VARCHAR(255)  DEFAULT 'PENDING',
    reviewed_by                 INT,
    reviewed_at                 DATETIME,
    review_comments             TEXT,
    skill_gap_id                INT,
    learning_resource_id        INT,
    recommendation_source       VARCHAR(255)  DEFAULT 'AI_GENERATED',
    ai_model_version            VARCHAR(255),
    priority                    VARCHAR(255)  DEFAULT 'MEDIUM',
    sequence_order              INT           DEFAULT 1,
    estimated_hours             DECIMAL(6,2),
    estimated_weeks             INT,
    validation_method           VARCHAR(255)  DEFAULT 'KNOWLEDGE_ASSESSMENT',
    started_at                  DATETIME,
    completed_at                DATETIME,
    created_by                  INT,
    created_at                  DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at                  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (learning_recommendation_id)
) ENGINE=InnoDB;

CREATE TABLE skill_evidence (
    evidence_id                 INT           NOT NULL AUTO_INCREMENT,
    employee_skill_id           INT,
    evidence_type               VARCHAR(255),
    evidence_title              VARCHAR(255),
    evidence_description        TEXT,
    score                       DECIMAL(6,2),
    maximum_score               DECIMAL(6,2),
    evidence_date               DATE,
    valid_until                 DATE,
    file_reference              VARCHAR(255),
    verification_status         VARCHAR(255)  DEFAULT 'PENDING',
    verified_by                 INT,
    verified_at                 DATETIME,
    created_at                  DATETIME      DEFAULT CURRENT_TIMESTAMP,
    learning_recommendation_id  INT,
    recorded_by                 INT,
    PRIMARY KEY (evidence_id)
) ENGINE=InnoDB;

CREATE TABLE audit_logs (
    audit_log_id        BIGINT       NOT NULL AUTO_INCREMENT,
    user_id             INT,
    action              VARCHAR(255),
    entity_name         VARCHAR(255),
    affected_record_id  BIGINT,
    old_values          JSON,
    new_values          JSON,
    action_timestamp    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    source_ip           VARCHAR(255),
    remarks             TEXT,
    PRIMARY KEY (audit_log_id)
) ENGINE=InnoDB;

-- =====================================================================
--  2. DATA
-- =====================================================================

-- ---- app_users : ONLY the admin  (password = admin@123, BCrypt) ------
INSERT INTO app_users (user_id, username, full_name, email, password_hash, role, is_active) VALUES
(1, 'admin', 'System Administrator', 'admin@rms.local',
 '$2a$10$0kUZlbyojdrJDtiSdTUive8eykyWELsopbKX.07atTOafHpFYraM2', 'ADMIN', 1);

-- ---- locations --------------------------------------------------------
INSERT INTO locations (location_id, location_name, city, state_name, country_name, is_active) VALUES
(1, 'Bhubaneswar Campus', 'Bhubaneswar', 'Odisha',      'India', 1),
(2, 'Bengaluru Tech Park','Bengaluru',   'Karnataka',   'India', 1),
(3, 'Pune Hinjewadi',     'Pune',        'Maharashtra', 'India', 1),
(4, 'Hyderabad HITEC',    'Hyderabad',   'Telangana',   'India', 1),
(5, 'Remote',             'Remote',      'Remote',      'India', 1);

-- ---- departments ------------------------------------------------------
INSERT INTO departments (department_id, department_name, is_active) VALUES
(1, 'Engineering',       1),
(2, 'Quality Assurance', 1),
(3, 'Data & Analytics',  1),
(4, 'DevOps & Cloud',    1),
(5, 'UI/UX Design',      1);

-- ---- designations -----------------------------------------------------
INSERT INTO designations (designation_id, designation_name) VALUES
(1, 'Software Engineer'),
(2, 'Senior Software Engineer'),
(3, 'Tech Lead'),
(4, 'QA Engineer'),
(5, 'Data Engineer'),
(6, 'DevOps Engineer'),
(7, 'UI/UX Designer'),
(8, 'Project Manager');

-- ---- skills -----------------------------------------------------------
INSERT INTO skills (skill_id, skill_name, skill_category) VALUES
(1,  'Java',           'Backend'),
(2,  'Spring Boot',    'Backend'),
(3,  'Hibernate/JPA',  'Backend'),
(4,  'MySQL',          'Database'),
(5,  'Angular',        'Frontend'),
(6,  'React',          'Frontend'),
(7,  'TypeScript',     'Frontend'),
(8,  'Python',         'Data'),
(9,  'Docker',         'DevOps'),
(10, 'Kubernetes',     'DevOps'),
(11, 'AWS',            'DevOps'),
(12, 'Jenkins',        'DevOps'),
(13, 'Selenium',       'Testing'),
(14, 'JUnit',          'Testing'),
(15, 'Apache Spark',   'Data'),
(16, 'Power BI',       'Data'),
(17, 'Figma',          'Design'),
(18, 'REST API Design','Backend'),
(19, 'Microservices',  'Backend'),
(20, 'Git',            'DevOps');

-- ---- employees --------------------------------------------------------
INSERT INTO employees
(employee_id, employee_code, employee_name, email, phone, total_experience_years,
 designation_id, department_id, location_id, date_of_joining,
 employment_status, source_system, erp_employee_id, erp_last_synced_at) VALUES
(1,  'EMP001', 'Aarav Mohanty',    'aarav.mohanty@example.com',    '9800000001',  6.0, 2, 1, 1, '2020-06-15', 'ACTIVE', 'HRMS', 'ERP1001', NOW()),
(2,  'EMP002', 'Priya Nayak',      'priya.nayak@example.com',      '9800000002',  9.5, 3, 1, 2, '2017-03-01', 'ACTIVE', 'HRMS', 'ERP1002', NOW()),
(3,  'EMP003', 'Rohan Das',        'rohan.das@example.com',        '9800000003',  2.5, 1, 1, 1, '2024-01-08', 'ACTIVE', 'HRMS', 'ERP1003', NOW()),
(4,  'EMP004', 'Sneha Patra',      'sneha.patra@example.com',      '9800000004',  5.0, 2, 1, 3, '2021-07-19', 'ACTIVE', 'HRMS', 'ERP1004', NOW()),
(5,  'EMP005', 'Vikram Singh',     'vikram.singh@example.com',     '9800000005',  3.0, 1, 1, 4, '2023-02-13', 'ACTIVE', 'HRMS', 'ERP1005', NOW()),
(6,  'EMP006', 'Ananya Rao',       'ananya.rao@example.com',       '9800000006',  4.5, 2, 1, 2, '2022-04-25', 'ACTIVE', 'HRMS', 'ERP1006', NOW()),
(7,  'EMP007', 'Kiran Kumar',      'kiran.kumar@example.com',      '9800000007',  2.0, 1, 1, 5, '2024-08-05', 'ACTIVE', 'HRMS', 'ERP1007', NOW()),
(8,  'EMP008', 'Meera Joshi',      'meera.joshi@example.com',      '9800000008',  3.5, 4, 2, 3, '2022-11-14', 'ACTIVE', 'HRMS', 'ERP1008', NOW()),
(9,  'EMP009', 'Sanjay Behera',    'sanjay.behera@example.com',    '9800000009',  5.0, 4, 2, 1, '2021-05-10', 'ACTIVE', 'HRMS', 'ERP1009', NOW()),
(10, 'EMP010', 'Neha Verma',       'neha.verma@example.com',       '9800000010',  1.5, 4, 2, 2, '2025-03-17', 'ACTIVE', 'HRMS', 'ERP1010', NOW()),
(11, 'EMP011', 'Arjun Reddy',      'arjun.reddy@example.com',      '9800000011',  4.0, 5, 3, 4, '2022-09-05', 'ACTIVE', 'HRMS', 'ERP1011', NOW()),
(12, 'EMP012', 'Divya Menon',      'divya.menon@example.com',      '9800000012',  6.5, 5, 3, 2, '2019-12-02', 'ACTIVE', 'HRMS', 'ERP1012', NOW()),
(13, 'EMP013', 'Karthik Iyer',     'karthik.iyer@example.com',     '9800000013',  2.5, 5, 3, 5, '2024-05-20', 'ACTIVE', 'HRMS', 'ERP1013', NOW()),
(14, 'EMP014', 'Suresh Pradhan',   'suresh.pradhan@example.com',   '9800000014',  7.0, 6, 4, 1, '2018-10-22', 'ACTIVE', 'HRMS', 'ERP1014', NOW()),
(15, 'EMP015', 'Lakshmi Narayan',  'lakshmi.narayan@example.com',  '9800000015',  3.5, 6, 4, 4, '2023-01-09', 'ACTIVE', 'HRMS', 'ERP1015', NOW()),
(16, 'EMP016', 'Rahul Sharma',     'rahul.sharma@example.com',     '9800000016',  5.5, 6, 4, 3, '2021-02-01', 'ACTIVE', 'HRMS', 'ERP1016', NOW()),
(17, 'EMP017', 'Pooja Mishra',     'pooja.mishra@example.com',     '9800000017',  4.0, 7, 5, 2, '2022-08-29', 'ACTIVE', 'HRMS', 'ERP1017', NOW()),
(18, 'EMP018', 'Aditya Kulkarni',  'aditya.kulkarni@example.com',  '9800000018',  2.5, 7, 5, 3, '2024-06-03', 'ACTIVE', 'HRMS', 'ERP1018', NOW()),
(19, 'EMP019', 'Swati Dash',       'swati.dash@example.com',       '9800000019', 11.0, 8, 1, 1, '2015-04-06', 'ACTIVE', 'HRMS', 'ERP1019', NOW()),
(20, 'EMP020', 'Manish Gupta',     'manish.gupta@example.com',     '9800000020',  6.0, 2, 1, 5, '2020-09-14', 'ACTIVE', 'HRMS', 'ERP1020', NOW());

-- ---- employee_skills  (proficiency 1-5, experience in months) ---------
-- columns: employee_id, skill_id, skill_priority, proficiency_level, experience_months, validation_status, information_source
INSERT INTO employee_skills
(employee_id, skill_id, skill_priority, proficiency_level, experience_months, validation_status, information_source) VALUES
-- E1 Aarav (ids 1-4)
(1, 1,  'PRIMARY',   '5', 72, 'VALIDATED',     'HR_SYSTEM'),
(1, 2,  'PRIMARY',   '4', 60, 'VALIDATED',     'HR_SYSTEM'),
(1, 4,  'SECONDARY', '4', 60, 'VALIDATED',     'HR_SYSTEM'),
(1, 3,  'SECONDARY', '4', 48, 'VALIDATED',     'HR_SYSTEM'),
-- E2 Priya (ids 5-9)
(2, 1,  'PRIMARY',   '5', 114, 'VALIDATED',    'HR_SYSTEM'),
(2, 2,  'PRIMARY',   '5', 96,  'VALIDATED',    'HR_SYSTEM'),
(2, 19, 'PRIMARY',   '5', 84,  'VALIDATED',    'HR_SYSTEM'),
(2, 11, 'SECONDARY', '3', 36,  'SELF_DECLARED','SELF'),
(2, 18, 'SECONDARY', '5', 90,  'VALIDATED',    'HR_SYSTEM'),
-- E3 Rohan
(3, 1,  'PRIMARY',   '3', 30, 'SELF_DECLARED', 'SELF'),
(3, 2,  'PRIMARY',   '3', 24, 'SELF_DECLARED', 'SELF'),
(3, 4,  'SECONDARY', '3', 24, 'SELF_DECLARED', 'SELF'),
(3, 20, 'SECONDARY', '3', 30, 'SELF_DECLARED', 'SELF'),
-- E4 Sneha
(4, 1,  'PRIMARY',   '4', 60, 'VALIDATED',     'HR_SYSTEM'),
(4, 2,  'PRIMARY',   '4', 48, 'VALIDATED',     'HR_SYSTEM'),
(4, 3,  'SECONDARY', '4', 40, 'VALIDATED',     'HR_SYSTEM'),
(4, 18, 'SECONDARY', '4', 48, 'VALIDATED',     'HR_SYSTEM'),
(4, 9,  'SECONDARY', '3', 24, 'SELF_DECLARED', 'SELF'),
-- E5 Vikram
(5, 1,  'PRIMARY',   '3', 36, 'SELF_DECLARED', 'SELF'),
(5, 2,  'PRIMARY',   '3', 30, 'SELF_DECLARED', 'SELF'),
(5, 19, 'SECONDARY', '2', 12, 'SELF_DECLARED', 'SELF'),
(5, 4,  'SECONDARY', '3', 30, 'SELF_DECLARED', 'SELF'),
-- E6 Ananya
(6, 5,  'PRIMARY',   '5', 54, 'VALIDATED',     'HR_SYSTEM'),
(6, 7,  'PRIMARY',   '5', 54, 'VALIDATED',     'HR_SYSTEM'),
(6, 18, 'SECONDARY', '3', 30, 'SELF_DECLARED', 'SELF'),
(6, 20, 'SECONDARY', '4', 54, 'VALIDATED',     'HR_SYSTEM'),
-- E7 Kiran
(7, 6,  'PRIMARY',   '4', 24, 'VALIDATED',     'HR_SYSTEM'),
(7, 7,  'PRIMARY',   '3', 20, 'SELF_DECLARED', 'SELF'),
(7, 5,  'SECONDARY', '2', 8,  'SELF_DECLARED', 'SELF'),
-- E8 Meera
(8, 13, 'PRIMARY',   '4', 42, 'VALIDATED',     'HR_SYSTEM'),
(8, 14, 'PRIMARY',   '3', 30, 'SELF_DECLARED', 'SELF'),
(8, 1,  'SECONDARY', '3', 24, 'SELF_DECLARED', 'SELF'),
(8, 8,  'SECONDARY', '2', 12, 'SELF_DECLARED', 'SELF'),
-- E9 Sanjay
(9, 13, 'PRIMARY',   '5', 60, 'VALIDATED',     'HR_SYSTEM'),
(9, 14, 'PRIMARY',   '4', 48, 'VALIDATED',     'HR_SYSTEM'),
(9, 1,  'SECONDARY', '3', 36, 'SELF_DECLARED', 'SELF'),
(9, 12, 'SECONDARY', '3', 24, 'SELF_DECLARED', 'SELF'),
-- E10 Neha
(10, 13,'PRIMARY',   '3', 18, 'SELF_DECLARED', 'SELF'),
(10, 14,'SECONDARY', '2', 12, 'SELF_DECLARED', 'SELF'),
(10, 8, 'SECONDARY', '2', 10, 'SELF_DECLARED', 'SELF'),
-- E11 Arjun
(11, 8, 'PRIMARY',   '4', 48, 'VALIDATED',     'HR_SYSTEM'),
(11, 15,'PRIMARY',   '4', 36, 'VALIDATED',     'HR_SYSTEM'),
(11, 4, 'SECONDARY', '4', 48, 'VALIDATED',     'HR_SYSTEM'),
(11, 11,'SECONDARY', '3', 24, 'SELF_DECLARED', 'SELF'),
-- E12 Divya
(12, 8, 'PRIMARY',   '5', 78, 'VALIDATED',     'HR_SYSTEM'),
(12, 15,'PRIMARY',   '5', 66, 'VALIDATED',     'HR_SYSTEM'),
(12, 16,'SECONDARY', '4', 48, 'VALIDATED',     'HR_SYSTEM'),
(12, 4, 'SECONDARY', '4', 60, 'VALIDATED',     'HR_SYSTEM'),
-- E13 Karthik
(13, 8, 'PRIMARY',   '3', 30, 'SELF_DECLARED', 'SELF'),
(13, 16,'SECONDARY', '3', 24, 'SELF_DECLARED', 'SELF'),
(13, 4, 'SECONDARY', '3', 24, 'SELF_DECLARED', 'SELF'),
-- E14 Suresh
(14, 9, 'PRIMARY',   '5', 84, 'VALIDATED',     'HR_SYSTEM'),
(14, 10,'PRIMARY',   '5', 72, 'VALIDATED',     'HR_SYSTEM'),
(14, 11,'PRIMARY',   '5', 72, 'VALIDATED',     'HR_SYSTEM'),
(14, 12,'SECONDARY', '4', 60, 'VALIDATED',     'HR_SYSTEM'),
(14, 20,'SECONDARY', '5', 84, 'VALIDATED',     'HR_SYSTEM'),
-- E15 Lakshmi
(15, 9, 'PRIMARY',   '4', 42, 'VALIDATED',     'HR_SYSTEM'),
(15, 10,'SECONDARY', '3', 24, 'SELF_DECLARED', 'SELF'),
(15, 11,'SECONDARY', '3', 30, 'SELF_DECLARED', 'SELF'),
(15, 12,'SECONDARY', '3', 30, 'SELF_DECLARED', 'SELF'),
-- E16 Rahul
(16, 9, 'PRIMARY',   '4', 60, 'VALIDATED',     'HR_SYSTEM'),
(16, 10,'PRIMARY',   '4', 48, 'VALIDATED',     'HR_SYSTEM'),
(16, 11,'PRIMARY',   '4', 48, 'VALIDATED',     'HR_SYSTEM'),
(16, 12,'SECONDARY', '4', 54, 'VALIDATED',     'HR_SYSTEM'),
-- E17 Pooja
(17, 17,'PRIMARY',   '5', 48, 'VALIDATED',     'HR_SYSTEM'),
(17, 6, 'SECONDARY', '2', 12, 'SELF_DECLARED', 'SELF'),
-- E18 Aditya
(18, 17,'PRIMARY',   '4', 30, 'VALIDATED',     'HR_SYSTEM'),
(18, 5, 'SECONDARY', '2', 6,  'SELF_DECLARED', 'SELF'),
-- E19 Swati
(19, 20,'PRIMARY',   '4', 100,'VALIDATED',     'HR_SYSTEM'),
(19, 1, 'SECONDARY', '3', 36, 'SELF_DECLARED', 'SELF'),
(19, 19,'SECONDARY', '3', 36, 'SELF_DECLARED', 'SELF'),
-- E20 Manish
(20, 1, 'PRIMARY',   '5', 72, 'VALIDATED',     'HR_SYSTEM'),
(20, 2, 'PRIMARY',   '5', 66, 'VALIDATED',     'HR_SYSTEM'),
(20, 19,'PRIMARY',   '4', 48, 'VALIDATED',     'HR_SYSTEM'),
(20, 9, 'SECONDARY', '3', 24, 'SELF_DECLARED', 'SELF'),
(20, 10,'SECONDARY', '2', 12, 'SELF_DECLARED', 'SELF');

-- ---- resource_availability (history table; newest row per employee = current)
-- base rows (HR / seed) for every employee. Employee 20 is allocated 100% from 2026-11-01,
-- so his base row closes on that date and an [AUTO] ALLOCATED row follows (row 21).
-- Employee 10 is on extended leave (UNAVAILABLE).
INSERT INTO resource_availability
(availability_id, employee_id, availability_status, available_from, available_until,
 available_capacity_percentage, expected_duration_months, availability_notes,
 effective_from, effective_until, recorded_by) VALUES
(1,  1,  'BENCH', '2026-11-01', NULL, 100, NULL, 'Available for new assignment', '2026-01-01', NULL, 1),
(2,  2,  'BENCH', '2026-11-01', NULL, 100, NULL, 'Available for new assignment', '2026-01-01', NULL, 1),
(3,  3,  'BENCH', '2026-11-01', NULL, 100, NULL, 'Available for new assignment', '2026-01-01', NULL, 1),
(4,  4,  'BENCH', '2026-11-01', NULL, 100, NULL, 'Available for new assignment', '2026-01-01', NULL, 1),
(5,  5,  'BENCH', '2026-11-01', NULL, 100, NULL, 'Available for new assignment', '2026-01-01', NULL, 1),
(6,  6,  'BENCH', '2026-11-01', NULL, 100, NULL, 'Available for new assignment', '2026-01-01', NULL, 1),
(7,  7,  'BENCH', '2026-11-01', NULL, 100, NULL, 'Available for new assignment', '2026-01-01', NULL, 1),
(8,  8,  'BENCH', '2026-11-01', NULL, 100, NULL, 'Available for new assignment', '2026-01-01', NULL, 1),
(9,  9,  'BENCH', '2026-11-01', NULL, 100, NULL, 'Available for new assignment', '2026-01-01', NULL, 1),
(10, 10, 'UNAVAILABLE', '2027-01-15', NULL, 0, NULL, 'On extended leave', '2026-10-01', NULL, 1),
(11, 11, 'BENCH', '2026-11-01', NULL, 100, NULL, 'Available for new assignment', '2026-01-01', NULL, 1),
(12, 12, 'BENCH', '2026-11-01', NULL, 100, NULL, 'Available for new assignment', '2026-01-01', NULL, 1),
(13, 13, 'BENCH', '2026-11-01', NULL, 100, NULL, 'Available for new assignment', '2026-01-01', NULL, 1),
(14, 14, 'BENCH', '2026-11-01', NULL, 100, NULL, 'Available for new assignment', '2026-01-01', NULL, 1),
(15, 15, 'BENCH', '2026-11-01', NULL, 100, NULL, 'Available for new assignment', '2026-01-01', NULL, 1),
(16, 16, 'BENCH', '2026-11-01', NULL, 100, NULL, 'Available for new assignment', '2026-01-01', NULL, 1),
(17, 17, 'BENCH', '2026-11-01', NULL, 100, NULL, 'Available for new assignment', '2026-01-01', NULL, 1),
(18, 18, 'BENCH', '2026-11-01', NULL, 100, NULL, 'Available for new assignment', '2026-01-01', NULL, 1),
(19, 19, 'BENCH', '2026-11-01', NULL, 100, NULL, 'Available for new assignment', '2026-01-01', NULL, 1),
(20, 20, 'BENCH', '2026-01-01', NULL, 100, NULL, 'Available for new assignment', '2026-01-01', '2026-11-01', 1),
(21, 20, 'ALLOCATED', '2027-04-01', NULL, 0, 5, '[AUTO] Allocated 100% to REQ-001', '2026-11-01', NULL, 1);

-- ---- projects ---------------------------------------------------------
INSERT INTO projects
(project_id, project_code, project_name, project_domain, project_description, project_status, start_date, end_date, created_by) VALUES
(1, 'PRJ-001', 'Retail Banking Portal',          'Banking',    'Customer-facing internet banking portal with account, payments and statements modules.', 'ACTIVE', '2026-01-01', '2027-06-30', 1),
(2, 'PRJ-002', 'Healthcare Analytics Platform',  'Healthcare', 'Data platform for patient-flow analytics and hospital reporting dashboards.',            'ACTIVE', '2026-06-01', '2027-09-30', 1),
(3, 'PRJ-003', 'E-Commerce Revamp',              'Retail',     'Re-platforming of an existing e-commerce storefront to microservices.',                  'DRAFT',  '2027-01-15', '2027-12-31', 1);

-- ---- project_requirements --------------------------------------------
INSERT INTO project_requirements
(requirement_id, project_id, requirement_code, role_name, role_description, required_headcount,
 minimum_proficiency_level, minimum_experience_months, joining_date, availability_within_days,
 project_duration_months, location, work_mode, requirement_priority, requirement_owner, requirement_status, ip_address) VALUES
(1, 1, 'REQ-001', 'Senior Java Backend Developer', 'Design and build Spring Boot REST services for the banking portal.', 2, 4, 48, '2026-12-01', 30, 12, 'Bhubaneswar', 'HYBRID', 'HIGH',   1, 'OPEN',  '127.0.0.1'),
(2, 1, 'REQ-002', 'Angular Frontend Developer',    'Build responsive Angular UI for payments and statements.',            1, 3, 36, '2026-12-15', 30, 9,  'Bengaluru',   'HYBRID', 'MEDIUM', 1, 'OPEN',  '127.0.0.1'),
(3, 2, 'REQ-003', 'Data Engineer (Spark)',         'Build Spark pipelines feeding the analytics warehouse.',              1, 4, 36, '2026-12-01', 15, 12, 'Remote',      'REMOTE', 'HIGH',   1, 'OPEN',  '127.0.0.1'),
(4, 2, 'REQ-004', 'DevOps Engineer',               'Own CI/CD and Kubernetes deployments on AWS.',                        1, 4, 48, '2027-01-10', 45, 12, 'Hyderabad',   'ONSITE', 'MEDIUM', 1, 'DRAFT', '127.0.0.1');

-- ---- required_skills --------------------------------------------------
INSERT INTO required_skills (requirement_id, skill_id, is_mandatory, minimum_proficiency) VALUES
(1, 1,  1, 4), (1, 2, 1, 4), (1, 3, 0, 3), (1, 4, 0, 3), (1, 19, 0, 3),
(2, 5,  1, 4), (2, 7, 1, 3), (2, 18, 0, 3),
(3, 8,  1, 4), (3, 15, 1, 4), (3, 4, 0, 3), (3, 16, 0, 3),
(4, 9,  1, 4), (4, 10, 1, 4), (4, 11, 1, 4), (4, 12, 0, 3);

-- ---- employee_allocations --------------------------------------------
-- (AllocationStatusJob recalculates ACTIVE/PLANNED/COMPLETED from dates at startup)
INSERT INTO employee_allocations
(allocation_id, employee_id, requirement_id, allocation_percentage, allocation_status, start_date, end_date, created_by) VALUES
(1, 20, 1, 100, 'ACTIVE',  '2026-11-01', '2027-03-31', 1),
(2, 6,  2, 100, 'PLANNED', '2026-12-15', '2027-09-15', 1),
(3, 12, 3, 50,  'PLANNED', '2026-12-01', '2027-06-30', 1);

-- ---- matching_runs ----------------------------------------------------
INSERT INTO matching_runs
(matching_run_id, requirement_id, scoring_configuration_version, algorithm_name, algorithm_version, model_version,
 candidate_pool_size, run_status, executed_by, completed_at, run_trigger) VALUES
(1, 1, 'v1', 'RULE_BASED_PLUS_LLM', '1.0', 'gemini', 15, 'COMPLETED', 1, NOW(), 'NEW_SEARCH'),
(2, 2, 'v1', 'RULE_BASED_PLUS_LLM', '1.0', 'gemini', 8,  'COMPLETED', 1, NOW(), 'NEW_SEARCH'),
(3, 3, 'v1', 'RULE_BASED_PLUS_LLM', '1.0', 'gemini', 6,  'COMPLETED', 1, NOW(), 'NEW_SEARCH');

-- ---- matching_results -------------------------------------------------
INSERT INTO matching_results
(matching_result_id, matching_run_id, employee_id, overall_score, match_category, strengths, skill_gaps, explanation, rank_position, recommendation_status) VALUES
(1, 1, 2,  92.50, 'EXCELLENT', '["Java","Spring Boot","Microservices","REST API Design"]', '[]',
   'Tech lead with 9+ years of Java/Spring Boot; exceeds every mandatory requirement.', 1, 'RECOMMENDED'),
(2, 1, 20, 89.00, 'EXCELLENT', '["Java","Spring Boot","Microservices"]', '[]',
   'Strong Java/Spring Boot profile with microservices experience.', 2, 'RECOMMENDED'),
(3, 1, 4,  81.00, 'GOOD',      '["Java","Spring Boot","Hibernate/JPA","REST API Design"]', '["Microservices"]',
   'Meets mandatory skills; lacks microservices exposure.', 3, 'RECOMMENDED'),
(4, 1, 1,  76.00, 'GOOD',      '["Java","Spring Boot","MySQL","Hibernate/JPA"]', '["Microservices"]',
   'Solid backend skills; microservices is a gap.', 4, 'REVIEW_REQUIRED'),
(5, 1, 5,  58.00, 'PARTIAL',   '["Java","Spring Boot","MySQL"]', '["Java below required","Spring Boot below required","Hibernate/JPA","Microservices below required"]',
   'Mandatory skills are one level below requirement; needs upskilling.', 5, 'REVIEW_REQUIRED'),
(6, 2, 6,  90.00, 'EXCELLENT', '["Angular","TypeScript"]', '[]',
   'Expert-level Angular and TypeScript.', 1, 'RECOMMENDED'),
(7, 2, 7,  62.00, 'PARTIAL',   '["TypeScript","React"]', '["Angular below required"]',
   'React background; Angular is below required level.', 2, 'REVIEW_REQUIRED'),
(8, 3, 12, 94.00, 'EXCELLENT', '["Python","Apache Spark","Power BI","MySQL"]', '[]',
   'Senior data engineer, expert in Spark.', 1, 'RECOMMENDED'),
(9, 3, 11, 82.00, 'GOOD',      '["Python","Apache Spark","MySQL"]', '["Power BI"]',
   'Meets mandatory skills; no Power BI.', 2, 'RECOMMENDED');

-- ---- allocation_decisions --------------------------------------------
INSERT INTO allocation_decisions
(allocation_decision_id, matching_result_id, requirement_id, allocation_id, decision, decision_reason, decision_maker) VALUES
(1, 2, 1, 1,    'SELECTED', 'Best immediate fit with availability from Nov 2026.', 1),
(2, 6, 2, 2,    'SELECTED', 'Expert Angular profile.', 1),
(3, 8, 3, 3,    'SELECTED', 'Allocated 50% to keep capacity for other analytics work.', 1),
(4, 5, 1, NULL, 'REJECTED', 'Below required proficiency on mandatory skills.', 1);

-- ---- skill_gaps -------------------------------------------------------
INSERT INTO skill_gaps
(skill_gap_id, matching_result_id, skill_id, gap_type, is_mandatory, required_proficiency, current_proficiency, gap_size, priority, priority_reason) VALUES
(1, 5, 1,  'BELOW_REQUIRED', 1, 4, 3,    1, 'HIGH',   'Mandatory skill one level below requirement'),
(2, 5, 2,  'BELOW_REQUIRED', 1, 4, 3,    1, 'HIGH',   'Mandatory skill one level below requirement'),
(3, 5, 3,  'MISSING',        0, 3, NULL, 3, 'MEDIUM', 'Optional skill missing'),
(4, 5, 19, 'BELOW_REQUIRED', 0, 3, 2,    1, 'LOW',    'Optional skill slightly below requirement'),
(5, 4, 19, 'MISSING',        0, 3, NULL, 3, 'MEDIUM', 'Optional skill missing'),
(6, 7, 5,  'BELOW_REQUIRED', 1, 4, 2,    2, 'CRITICAL','Mandatory skill two levels below requirement');

-- ---- learning_resources (L&K training repository) --------------------
INSERT INTO learning_resources
(learning_resource_id, skill_id, resource_title, resource_type, provider, resource_url, resource_description,
 from_proficiency, to_proficiency, estimated_hours, validation_method, is_approved, approved_by, approved_at, is_active, created_by) VALUES
(1, 1,  'Advanced Java Programming',        'COURSE',        'Internal L&K', 'https://lnd.example.com/courses/java-advanced',   'Concurrency, streams, JVM internals.',             3, 5, 30.00, 'KNOWLEDGE_ASSESSMENT', 1, 1, NOW(), 1, 1),
(2, 2,  'Spring Boot in Depth',             'COURSE',        'Internal L&K', 'https://lnd.example.com/courses/spring-boot',     'Auto-configuration, security, testing.',           3, 5, 24.00, 'KNOWLEDGE_ASSESSMENT', 1, 1, NOW(), 1, 1),
(3, 3,  'Hibernate & JPA Fundamentals',     'COURSE',        'Internal L&K', 'https://lnd.example.com/courses/hibernate',       'Mappings, fetching strategies, performance.',      1, 4, 18.00, 'KNOWLEDGE_ASSESSMENT', 1, 1, NOW(), 1, 1),
(4, 19, 'Microservices Architecture',       'COURSE',        'Internal L&K', 'https://lnd.example.com/courses/microservices',   'Service design, messaging, resilience patterns.',  1, 4, 28.00, 'PRACTICAL_ASSIGNMENT', 1, 1, NOW(), 1, 1),
(5, 5,  'Angular Complete Guide',           'COURSE',        'Internal L&K', 'https://lnd.example.com/courses/angular',         'Components, RxJS, routing, state management.',     1, 4, 36.00, 'PRACTICAL_ASSIGNMENT', 1, 1, NOW(), 1, 1),
(6, 9,  'Docker for Developers',            'WORKSHOP',      'Internal L&K', 'https://lnd.example.com/workshops/docker',        'Images, compose, networking.',                     1, 4, 12.00, 'KNOWLEDGE_ASSESSMENT', 1, 1, NOW(), 1, 1),
(7, 10, 'Kubernetes Certification Prep',    'CERTIFICATION', 'Internal L&K', 'https://lnd.example.com/certs/kubernetes',        'CKA exam preparation.',                            2, 5, 40.00, 'CERTIFICATION',        1, 1, NOW(), 1, 1),
(8, 15, 'Apache Spark Essentials',          'COURSE',        'Internal L&K', 'https://lnd.example.com/courses/spark',           'RDD, DataFrame, performance tuning.',              1, 4, 26.00, 'KNOWLEDGE_ASSESSMENT', 0, NULL, NULL, 1, 1);

-- ---- learning_recommendations ----------------------------------------
INSERT INTO learning_recommendations
(learning_recommendation_id, matching_result_id, skill_id, current_proficiency, target_proficiency, skill_gap_description,
 learning_action, learning_resource, resource_type, review_status, reviewed_by, reviewed_at, review_comments,
 skill_gap_id, learning_resource_id, recommendation_source, priority, sequence_order, estimated_hours, estimated_weeks,
 validation_method, created_by) VALUES
(1, 5, 1, 3, 4, 'Java is one level below the mandatory requirement.',
   'Complete advanced Java course and a practical assignment', 'Advanced Java Programming', 'COURSE',
   'APPROVED', 1, NOW(), 'Approved.', 1, 1, 'LK_REPOSITORY', 'HIGH', 1, 30.00, 6, 'KNOWLEDGE_ASSESSMENT', 1),
(2, 5, 2, 3, 4, 'Spring Boot is one level below the mandatory requirement.',
   'Complete Spring Boot in Depth', 'Spring Boot in Depth', 'COURSE',
   'PENDING', NULL, NULL, NULL, 2, 2, 'LK_REPOSITORY', 'HIGH', 2, 24.00, 5, 'KNOWLEDGE_ASSESSMENT', 1),
(3, 4, 19, NULL, 3, 'No microservices exposure.',
   'Take the microservices architecture course', 'Microservices Architecture', 'COURSE',
   'PENDING', NULL, NULL, NULL, 5, 4, 'LK_REPOSITORY', 'MEDIUM', 1, 28.00, 6, 'PRACTICAL_ASSIGNMENT', 1);

-- ---- skill_evidence ---------------------------------------------------
-- employee_skill_id 5 = Priya/Java, 6 = Priya/Spring Boot, 1 = Aarav/Java
INSERT INTO skill_evidence
(evidence_id, employee_skill_id, evidence_type, evidence_title, evidence_description, score, maximum_score,
 evidence_date, valid_until, file_reference, verification_status, verified_by, verified_at, recorded_by) VALUES
(1, 5, 'CERTIFICATION', 'Oracle Certified Professional: Java SE 17', 'Official certification.',        88.00, 100.00, '2025-05-10', '2028-05-10', 'certs/priya_java17.pdf', 'VERIFIED', 1, NOW(), 1),
(2, 6, 'ASSESSMENT',    'Spring Boot Internal Assessment',            'Scored in the top band.',         91.00, 100.00, '2026-02-20', NULL,         NULL,                     'VERIFIED', 1, NOW(), 1),
(3, 1, 'PROJECT_EXPERIENCE', 'Core Banking Services Delivery',        'Delivered payments microservice.', NULL,  NULL,  '2025-11-30', NULL,         NULL,                     'PENDING',  NULL, NULL, 1);

-- ---- audit_logs -------------------------------------------------------
INSERT INTO audit_logs (user_id, action, entity_name, affected_record_id, old_values, new_values, source_ip, remarks) VALUES
(1, 'CREATE', 'ProjectRequirement', 1, NULL, '{"requirementCode":"REQ-001","status":"OPEN"}', '127.0.0.1', 'Requirement created (seed data)'),
(1, 'CREATE', 'EmployeeAllocation', 1, NULL, '{"employeeId":20,"requirementId":1,"allocationPercentage":100}', '127.0.0.1', 'Allocation confirmed (seed data)'),
(1, 'UPDATE', 'LearningRecommendation', 1, '{"reviewStatus":"PENDING"}', '{"reviewStatus":"APPROVED"}', '127.0.0.1', 'Recommendation approved (seed data)');

-- =====================================================================
--  Quick check after import:
--    SELECT username, role, is_active FROM app_users;     -> admin / ADMIN / 1
--    SELECT COUNT(*) FROM employees;                      -> 20
-- =====================================================================

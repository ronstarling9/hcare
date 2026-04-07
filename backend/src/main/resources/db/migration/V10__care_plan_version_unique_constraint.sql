ALTER TABLE care_plans
    ADD CONSTRAINT uq_care_plans_client_version UNIQUE (client_id, plan_version);

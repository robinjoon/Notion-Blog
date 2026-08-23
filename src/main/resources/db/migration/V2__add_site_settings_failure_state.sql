alter table site_settings alter column root_page_id drop not null;
alter table site_settings add column failure_count integer not null default 0;
alter table site_settings add constraint site_settings_failure_count_check check (failure_count >= 0);

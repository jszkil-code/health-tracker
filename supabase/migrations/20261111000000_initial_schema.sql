-- Phase 1 schema: every table from docs/SPEC.md "Data model", with sync columns and RLS.
-- Never edit this file after it has been applied; add a new migration instead.

create extension if not exists pgcrypto with schema extensions;

-- ---------------------------------------------------------------------------
-- Shared sync trigger
-- ---------------------------------------------------------------------------
-- Conflicts resolve by latest updated_at (last write wins), enforced server-side:
--   * an update carrying an older updated_at than the stored row is ignored;
--   * an update that doesn't change updated_at gets now().
create or replace function public.sync_before_update()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if new.updated_at < old.updated_at then
    return null; -- stale write: skip this row, keep the newer one
  end if;
  if new.updated_at = old.updated_at then
    new.updated_at := now();
  end if;
  new.user_id := old.user_id; -- rows never change owner
  return new;
end;
$$;

-- Adds the trigger, RLS, grants and the standard owner policy to a synced table.
-- client_writable = false makes the table read-only for the app (server functions write
-- with the service role, which bypasses RLS).
create or replace function public.setup_synced_table(tbl regclass, client_writable boolean)
returns void
language plpgsql
set search_path = ''
as $$
begin
  execute format('create trigger sync_before_update before update on %s
                  for each row execute function public.sync_before_update()', tbl);
  execute format('alter table %s enable row level security', tbl);
  execute format('revoke all on table %s from anon, authenticated', tbl);
  execute format('create policy "owner can read" on %s for select to authenticated
                  using (user_id = (select auth.uid()))', tbl);
  if client_writable then
    execute format('grant select, insert, update, delete on table %s to authenticated', tbl);
    execute format('create policy "owner can insert" on %s for insert to authenticated
                    with check (user_id = (select auth.uid()))', tbl);
    execute format('create policy "owner can update" on %s for update to authenticated
                    using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()))', tbl);
    execute format('create policy "owner can delete" on %s for delete to authenticated
                    using (user_id = (select auth.uid()))', tbl);
  else
    execute format('grant select on table %s to authenticated', tbl);
  end if;
end;
$$;

-- Common columns, repeated per table (Postgres has no column mixins):
--   id               uuid primary key
--   user_id          owner, defaults to the caller
--   source           where the row came from: health_connect, polar, app, cookbook, server
--   source_record_id id of the record in that source; (user_id, source, source_record_id) is unique
--   created_at / updated_at / deleted_at (soft delete)
-- Units are metric: kg, g, kcal, ms, m, seconds for durations.

-- ---------------------------------------------------------------------------
-- Device data
-- ---------------------------------------------------------------------------
create table public.workouts (
  id               uuid primary key default gen_random_uuid(),
  user_id          uuid not null default auth.uid() references auth.users (id) on delete cascade,
  source           text not null,
  source_record_id text not null,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now(),
  deleted_at       timestamptz,
  workout_type     text not null,
  start_time       timestamptz not null,
  end_time         timestamptz not null,
  duration_s       integer check (duration_s >= 0),
  avg_hr           smallint check (avg_hr > 0),
  max_hr           smallint check (max_hr > 0),
  calories_kcal    numeric(8, 1) check (calories_kcal >= 0),
  distance_m       numeric(10, 1) check (distance_m >= 0),
  cardio_load      numeric(8, 2),
  unique (user_id, source, source_record_id),
  check (end_time >= start_time)
);
create index workouts_user_start_idx on public.workouts (user_id, start_time desc);

create table public.sleep_sessions (
  id               uuid primary key default gen_random_uuid(),
  user_id          uuid not null default auth.uid() references auth.users (id) on delete cascade,
  source           text not null,
  source_record_id text not null,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now(),
  deleted_at       timestamptz,
  start_time       timestamptz not null,
  end_time         timestamptz not null,
  total_sleep_s    integer check (total_sleep_s >= 0),
  -- [{ "stage": "deep", "start": "...", "end": "..." }, ...]
  stages           jsonb not null default '[]'::jsonb,
  unique (user_id, source, source_record_id),
  check (end_time >= start_time)
);
create index sleep_sessions_user_start_idx on public.sleep_sessions (user_id, start_time desc);

create table public.recovery_daily (
  id                      uuid primary key default gen_random_uuid(),
  user_id                 uuid not null default auth.uid() references auth.users (id) on delete cascade,
  source                  text not null default 'polar',
  source_record_id        text not null,
  created_at              timestamptz not null default now(),
  updated_at              timestamptz not null default now(),
  deleted_at              timestamptz,
  day                     date not null,
  nightly_recharge_status smallint,
  ans_charge              numeric(6, 2),
  sleep_charge            numeric(6, 2),
  unique (user_id, source, source_record_id)
);
create index recovery_daily_user_day_idx on public.recovery_daily (user_id, day desc);

create table public.vitals_daily (
  id               uuid primary key default gen_random_uuid(),
  user_id          uuid not null default auth.uid() references auth.users (id) on delete cascade,
  source           text not null,
  source_record_id text not null,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now(),
  deleted_at       timestamptz,
  day              date not null,
  resting_hr       smallint check (resting_hr > 0),
  hrv_rmssd_ms     numeric(6, 1) check (hrv_rmssd_ms >= 0),
  steps            integer check (steps >= 0),
  active_kcal      numeric(8, 1) check (active_kcal >= 0),
  total_kcal       numeric(8, 1) check (total_kcal >= 0),
  unique (user_id, source, source_record_id)
);
create index vitals_daily_user_day_idx on public.vitals_daily (user_id, day desc);

create table public.body_measurements (
  id               uuid primary key default gen_random_uuid(),
  user_id          uuid not null default auth.uid() references auth.users (id) on delete cascade,
  source           text not null,
  source_record_id text not null,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now(),
  deleted_at       timestamptz,
  measured_at      timestamptz not null,
  weight_kg        numeric(6, 2) check (weight_kg > 0),
  body_fat_pct     numeric(5, 2) check (body_fat_pct between 0 and 100),
  lean_mass_kg     numeric(6, 2) check (lean_mass_kg > 0),
  bone_mass_kg     numeric(5, 2) check (bone_mass_kg > 0),
  body_water_kg    numeric(6, 2) check (body_water_kg > 0),
  bmr_kcal         numeric(7, 1) check (bmr_kcal > 0),
  -- any further composition fields Withings sends (open question in the spec)
  extra            jsonb not null default '{}'::jsonb,
  unique (user_id, source, source_record_id)
);
create index body_measurements_user_time_idx on public.body_measurements (user_id, measured_at desc);

-- ---------------------------------------------------------------------------
-- Food
-- ---------------------------------------------------------------------------
create table public.recipes (
  id                     uuid primary key default gen_random_uuid(),
  user_id                uuid not null default auth.uid() references auth.users (id) on delete cascade,
  source                 text not null default 'cookbook',
  -- dedupe key: normalized title + source URL, so re-importing never duplicates
  source_record_id       text not null,
  created_at             timestamptz not null default now(),
  updated_at             timestamptz not null default now(),
  deleted_at             timestamptz,
  title                  text not null,
  source_url             text not null default '',
  source_label           text not null default '',
  servings               numeric(6, 2) check (servings > 0),
  prep_time              text not null default '',
  cook_time              text not null default '',
  category               text check (category in ('Dinner', 'Lunch', 'Breakfast', 'Dessert', 'Snack', 'Side')),
  image                  text not null default '',
  ingredients            jsonb not null default '[]'::jsonb,
  instructions           jsonb not null default '[]'::jsonb,
  notes                  text not null default '',
  -- per-serving nutrition, computed on import
  kcal_per_serving       numeric(8, 1),
  protein_g_per_serving  numeric(7, 1),
  carbs_g_per_serving    numeric(7, 1),
  fat_g_per_serving      numeric(7, 1),
  fiber_g_per_serving    numeric(7, 1),
  uncounted_ingredients  jsonb not null default '[]'::jsonb,
  nutrition_computed_at  timestamptz,
  imported_at            timestamptz not null default now(),
  unique (user_id, source, source_record_id)
);

create table public.meals (
  id                  uuid primary key default gen_random_uuid(),
  user_id             uuid not null default auth.uid() references auth.users (id) on delete cascade,
  source              text not null default 'app',
  source_record_id    text not null,
  created_at          timestamptz not null default now(),
  updated_at          timestamptz not null default now(),
  deleted_at          timestamptz,
  eaten_at            timestamptz not null,
  meal_type           text check (meal_type in ('breakfast', 'lunch', 'dinner', 'snack')),
  meal_source         text not null check (meal_source in ('photo', 'recipe', 'manual')),
  photo_path          text,
  total_weight_g      numeric(8, 1) check (total_weight_g > 0),
  recipe_id           uuid references public.recipes (id) on delete set null,
  servings            numeric(6, 2) check (servings > 0),
  status              text not null default 'draft' check (status in ('draft', 'confirmed')),
  clarifying_question text,
  total_kcal          numeric(8, 1),
  total_protein_g     numeric(7, 1),
  total_carbs_g       numeric(7, 1),
  total_fat_g         numeric(7, 1),
  total_fiber_g       numeric(7, 1),
  unique (user_id, source, source_record_id)
);
create index meals_user_eaten_idx on public.meals (user_id, eaten_at desc);

create table public.meal_items (
  id               uuid primary key default gen_random_uuid(),
  user_id          uuid not null default auth.uid() references auth.users (id) on delete cascade,
  source           text not null default 'app',
  source_record_id text not null,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now(),
  deleted_at       timestamptz,
  meal_id          uuid not null references public.meals (id) on delete cascade,
  food_name        text not null,
  fdc_id           integer,
  grams            numeric(8, 1) not null check (grams >= 0),
  kcal             numeric(8, 1),
  protein_g        numeric(7, 1),
  carbs_g          numeric(7, 1),
  fat_g            numeric(7, 1),
  fiber_g          numeric(7, 1),
  estimated        boolean not null default false,
  confidence       text check (confidence in ('low', 'medium', 'high')),
  unique (user_id, source, source_record_id)
);
create index meal_items_meal_idx on public.meal_items (meal_id);

-- ---------------------------------------------------------------------------
-- Coach and settings
-- ---------------------------------------------------------------------------
create table public.coach_notes (
  id               uuid primary key default gen_random_uuid(),
  user_id          uuid not null default auth.uid() references auth.users (id) on delete cascade,
  source           text not null default 'server',
  source_record_id text not null,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now(),
  deleted_at       timestamptz,
  note_date        date not null,
  input_summary    jsonb not null,
  guidance         jsonb not null,
  model            text not null,
  unique (user_id, source, source_record_id)
);
create index coach_notes_user_date_idx on public.coach_notes (user_id, note_date desc);

create table public.goals (
  id                 uuid primary key default gen_random_uuid(),
  user_id            uuid not null default auth.uid() references auth.users (id) on delete cascade,
  source             text not null default 'app',
  source_record_id   text not null default 'goals',
  created_at         timestamptz not null default now(),
  updated_at         timestamptz not null default now(),
  deleted_at         timestamptz,
  training_focus     text,
  kcal_target        numeric(7, 1),
  protein_g_target   numeric(6, 1),
  carbs_g_target     numeric(6, 1),
  fat_g_target       numeric(6, 1),
  display_units      text not null default 'us' check (display_units in ('us', 'metric')),
  preferences        jsonb not null default '{}'::jsonb,
  unique (user_id, source, source_record_id)
);

-- ---------------------------------------------------------------------------
-- Integration bookkeeping
-- ---------------------------------------------------------------------------
create table public.polar_raw (
  id               uuid primary key default gen_random_uuid(),
  user_id          uuid not null references auth.users (id) on delete cascade,
  source           text not null default 'polar',
  -- e.g. "<transaction id>/<data type>/<item id>"
  source_record_id text not null,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now(),
  deleted_at       timestamptz,
  transaction_id   bigint,
  data_type        text not null,
  payload          jsonb not null,
  fetched_at       timestamptz not null default now(),
  unique (user_id, source, source_record_id)
);
create index polar_raw_user_type_idx on public.polar_raw (user_id, data_type, fetched_at desc);

create table public.sync_state (
  id               uuid primary key default gen_random_uuid(),
  user_id          uuid not null default auth.uid() references auth.users (id) on delete cascade,
  -- source = the device or integration; source_record_id = the stream within it (e.g. a record type)
  source           text not null,
  source_record_id text not null,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now(),
  deleted_at       timestamptz,
  cursor           text,
  last_synced_at   timestamptz,
  unique (user_id, source, source_record_id)
);

select public.setup_synced_table('public.workouts', true);
select public.setup_synced_table('public.sleep_sessions', true);
select public.setup_synced_table('public.recovery_daily', false);
select public.setup_synced_table('public.vitals_daily', true);
select public.setup_synced_table('public.body_measurements', true);
select public.setup_synced_table('public.recipes', true);
select public.setup_synced_table('public.meals', true);
select public.setup_synced_table('public.meal_items', true);
select public.setup_synced_table('public.coach_notes', false);
select public.setup_synced_table('public.goals', true);
select public.setup_synced_table('public.polar_raw', false);
select public.setup_synced_table('public.sync_state', true);

-- Setup helper is only needed during migration.
drop function public.setup_synced_table(regclass, boolean);
revoke all on function public.sync_before_update() from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- Polar OAuth tokens: server only. RLS on with no policies and no grants, so neither
-- anon nor authenticated can read or write; only the service role (Edge Functions) can.
-- ---------------------------------------------------------------------------
create table public.polar_tokens (
  user_id        uuid primary key references auth.users (id) on delete cascade,
  polar_user_id  text not null,
  access_token   text not null,
  token_type     text not null default 'bearer',
  expires_at     timestamptz,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now()
);
alter table public.polar_tokens enable row level security;
revoke all on table public.polar_tokens from anon, authenticated;

-- ---------------------------------------------------------------------------
-- Meal photo storage: private bucket, files under "<user id>/...", read via signed URLs.
-- ---------------------------------------------------------------------------
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('meal-photos', 'meal-photos', false, 10485760, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do nothing;

create policy "meal photos: owner can read" on storage.objects for select to authenticated
  using (bucket_id = 'meal-photos' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy "meal photos: owner can upload" on storage.objects for insert to authenticated
  with check (bucket_id = 'meal-photos' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy "meal photos: owner can update" on storage.objects for update to authenticated
  using (bucket_id = 'meal-photos' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy "meal photos: owner can delete" on storage.objects for delete to authenticated
  using (bucket_id = 'meal-photos' and (storage.foldername(name))[1] = (select auth.uid())::text);

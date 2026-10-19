-- System notifications store message codes so they can be shown in the reader's language.
-- Existing rows keep only their text (title/message) and are displayed as before.
alter table notifications add column if not exists title_key varchar(255);
alter table notifications add column if not exists message_key varchar(255);
alter table notifications add column if not exists message_args text;

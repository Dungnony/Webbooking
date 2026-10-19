-- The hotel (answered by an admin) can reply once to each review; replying again overwrites it.
alter table reviews add column if not exists hotel_reply text;
alter table reviews add column if not exists hotel_replied_at timestamp(6);

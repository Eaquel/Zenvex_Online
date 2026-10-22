-- Varsayılan profil resmi artık NPC (Tüccar); kahraman/asker/köpek/zombi resimleri seçilemez.
alter table public.accounts alter column avatar set default 1;

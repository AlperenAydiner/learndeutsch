-- Secenek metni degisince eski satir silinebilmeli.
--
-- Bir sorunun sikki yanlis/belirsiz cikip degistirildiginde tohumlayici
-- eski satiri siler. Ama gecmis denemelerde o sik secilmis olabilir ve
-- attempt_answer.selected_option_id kisiti silmeyi engelliyordu.
--
-- Gecmis deneme kaybolmamali: hangi sikkin secildigi bilgisi bosalir,
-- dogru/yanlis bilgisi ve puan yerinde kalir.

ALTER TABLE attempt_answer
    DROP CONSTRAINT attempt_answer_selected_option_id_fkey;

ALTER TABLE attempt_answer
    ADD CONSTRAINT attempt_answer_selected_option_id_fkey
    FOREIGN KEY (selected_option_id) REFERENCES question_option (id)
    ON DELETE SET NULL;

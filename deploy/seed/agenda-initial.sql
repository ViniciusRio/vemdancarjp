BEGIN;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM events)
     OR EXISTS (SELECT 1 FROM variable_venues)
     OR EXISTS (SELECT 1 FROM other_venues) THEN
    RAISE EXCEPTION 'Agenda tables are not empty; nothing was changed';
  END IF;
END
$$;

INSERT INTO events (day_id, name, venue, neighborhood, instagram, frequency, sort_order) VALUES
  ('monday', 'Forró do Turista', 'Bessa Grill', 'Bessa', 'https://www.instagram.com/bessagrilljp', NULL, 0),
  ('wednesday', 'Gravação Cantos e Contos', 'Bessa Grill', 'Bessa', 'https://www.instagram.com/bessagrilljp', NULL, 1),
  ('wednesday', 'Prática Forrozeira Iandê', 'Iandê Cultura', 'Bessa', 'https://www.instagram.com/iandecultura', '1ª quarta do mês', 2),
  ('thursday', 'Recanto das Três Ruas', 'Recanto das Três Ruas', 'Bancários', 'https://www.instagram.com/recantotresruas', NULL, 3),
  ('friday', 'Bancários Grill', 'Bancários Grill', 'Bancários', 'https://www.instagram.com/bancariosgrill', NULL, 4),
  ('friday', 'Forró d''Os Fulano', 'Vila do Porto', 'Centro Histórico', 'https://www.instagram.com/vila_do_porto', '1ª sexta do mês', 5),
  ('saturday', 'Projeto Forró na Caixa', 'Praça Rio Branco', 'Centro', 'https://www.instagram.com/forronacaixajp', 'Sabadinho Bom', 6),
  ('saturday', 'Forró de Candeeiro', 'Castelo Branco', 'Castelo Branco', 'https://www.instagram.com/jefersonleiterabequeiro', '1º sábado do mês', 7),
  ('saturday', 'Pirâmide Shopping', 'Pirâmide Shopping', 'Tambaú', 'https://www.instagram.com/piramide_shopping', NULL, 8),
  ('saturday', 'Beer Garden', 'Beer Garden', 'Miramar', 'https://www.instagram.com/beergardenjp', NULL, 9),
  ('saturday', 'Casarão Criativo', 'Casarão Criativo', 'Centro', 'https://www.instagram.com/casaraocriativo', NULL, 10),
  ('sunday', 'Recanto das Três Ruas', 'Recanto das Três Ruas', 'Bancários', NULL, NULL, 11),
  ('sunday', 'Casarão Criativo', 'Casarão Criativo', 'Centro', NULL, 'verificar programação', 12);

INSERT INTO variable_venues (name, neighborhood, instagram, days, sort_order) VALUES
  ('Arpoador', 'Tambaú', 'https://www.instagram.com/arpoadorjp', 'Quinta a domingo', 0),
  ('Clube Cabo Branco', 'Miramar', 'https://www.instagram.com/clubecabobranco', 'Sexta a domingo', 1);

INSERT INTO other_venues (name, neighborhood, instagram, sort_order) VALUES
  ('Seu Antônio Bar', 'Bessa', 'https://www.instagram.com/seuantoniobar', 0),
  ('Barril 21', 'Bessa', 'https://www.instagram.com/barril21jp', 1),
  ('Dorys Prime', 'José Américo', 'https://www.instagram.com/dorysprime_oficial', 2),
  ('Priscilla''s Halls', 'Mandacaru', 'https://www.instagram.com/priscyllashall', 3),
  ('Beer Time', 'José Américo', 'https://www.instagram.com/beertimejp', 4),
  ('Forronejo', 'Altiplano', 'https://www.instagram.com/forronejomusicbar', 5),
  ('Mansão Music Bar', 'Geisel', 'https://www.instagram.com/mansaogeisel', 6);

COMMIT;

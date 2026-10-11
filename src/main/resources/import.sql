INSERT INTO leitores(cpf, nome, endereco, email) VALUES ('1234','Renally','rua x','rvs14@discente.ifpe.edu.br');
INSERT INTO leitores(cpf, nome, endereco, email) VALUES ('12345','Ana','rua y','acsb3@discente.ifpe.edu.br');
INSERT INTO leitores(cpf, nome, endereco, email) VALUES ('12346', 'Abigail', 'rua z', 'amgn@discente.ifpe.edu.br');
ALTER TABLE livros ALTER COLUMN disponivel SET NOT NULL;
ALTER TABLE livros ALTER COLUMN disponivel SET DEFAULT TRUE;
ALTER TABLE livros ALTER COLUMN status SET NOT NULL;
ALTER TABLE livros ALTER COLUMN status SET DEFAULT TRUE;
INSERT INTO livros (titulo, autor, editora) VALUES ('A hora da estrela','Clarice Lispector','Editora Rocco');
INSERT INTO livros (titulo, autor, editora) VALUES ('A natureza da mordida','Carla Madeira','Editora Record');


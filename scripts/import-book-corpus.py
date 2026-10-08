#!/usr/bin/env python3
"""Validate the local corpus and render one atomic PostgreSQL import.

Rendering does not connect to a database. Apply the result with psql -X -qAt
-v ON_ERROR_STOP=1, after a verified backup and with application writers stopped.
"""

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path


def sql_literal(value):
    if value is None:
        return "NULL"
    return "'" + value.replace("'", "''") + "'"


def copy_cell(value):
    if value is None:
        return r"\N"
    return (str(value).replace("\\", "\\\\").replace("\t", r"\t")
            .replace("\n", r"\n").replace("\r", r"\r"))


def text_pages(text, page_chars):
    return [text[start:start + page_chars] for start in range(0, len(text), page_chars)]


def load_books(corpus, page_chars):
    manifest_path = corpus / "out/manifest.json"
    titles_path = corpus / "out/book-titles.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    titles = json.loads(titles_path.read_text(encoding="utf-8"))
    indexed = {book["gutenberg_id"]: book for book in manifest["books"]}
    ids = [book["source_id"] for book in titles]
    if not titles or len(ids) != len(set(ids)) or len(indexed) != len(manifest["books"]):
        raise ValueError("The corpus is empty or contains duplicate source IDs.")
    if set(ids) != set(indexed) or manifest["with_text"] != len(titles):
        raise ValueError("The catalog and text manifest contain different books.")
    prepared = []
    for book in sorted(titles, key=lambda item: item["source_id"]):
        source_id = book["source_id"]
        if book["source"] != "gutenberg" or type(source_id) is not int or source_id <= 0:
            raise ValueError("Each book needs a positive Gutenberg source ID.")
        source = indexed[source_id]
        for key in ("isbn", "title", "authors", "category", "publisher", "publish_year", "price_cents"):
            if book[key] != source[key]:
                raise ValueError(f"Book {source_id}: {key} differs from the manifest.")
        row = dict(book, author="; ".join(book["authors"]))
        for key, limit in (("isbn", 20), ("title", 256), ("author", 256),
                           ("publisher", 128), ("category", 64)):
            value = row[key]
            if value is not None and (not isinstance(value, str) or len(value) > limit or "\x00" in value):
                raise ValueError(f"Book {source_id}: {key} exceeds its database constraint.")
        if not row["title"] or type(row["price_cents"]) is not int or row["price_cents"] < 0:
            raise ValueError(f"Book {source_id}: invalid title or price.")
        year = row["publish_year"]
        if year is not None and (type(year) is not int or not -32768 <= year <= 32767):
            raise ValueError(f"Book {source_id}: invalid publish_year.")
        text_path = (corpus / source["text_file"]).resolve()
        if not text_path.is_relative_to((corpus / "texts").resolve()):
            raise ValueError(f"Book {source_id}: text path is outside the texts directory.")
        raw = text_path.read_bytes()
        digest = hashlib.sha256(raw).hexdigest()
        if len(raw) != source["text_bytes"] or digest != source["text_sha256"]:
            raise ValueError(f"Book {source_id}: text checksum or size differs from the manifest.")
        text = raw.decode("utf-8")
        if not text.strip() or "\x00" in text:
            raise ValueError(f"Book {source_id}: empty text or a PostgreSQL-incompatible NUL.")
        prepared.append(dict(row, text_path=text_path, text_bytes=len(raw),
                             text_sha256=digest, language=source["language"],
                             page_count=len(text_pages(text, page_chars)),
                             copy_count=1 + source_id % 4))
    return prepared, {
        "manifest_sha256": hashlib.sha256(manifest_path.read_bytes()).hexdigest(),
        "catalog_sha256": hashlib.sha256(titles_path.read_bytes()).hexdigest(),
    }


def render(corpus, output, replace_categories, page_chars=4096):
    if not 256 <= page_chars <= 16384:
        raise ValueError("page_chars must be between 256 and 16384.")
    corpus = corpus.resolve()
    books, checksums = load_books(corpus, page_chars)
    output.mkdir(parents=True, exist_ok=False)
    sql_path = output / "import.sql"
    with sql_path.open("w", encoding="utf-8", newline="\n") as stream:
        stream.write("\\set ON_ERROR_STOP on\nBEGIN;\nSET LOCAL standard_conforming_strings = on;\n"
                     "SET LOCAL lock_timeout = '10s';\nSET LOCAL client_encoding = 'UTF8';\n"
                     "LOCK TABLE book_title, book_copy, book_title_content_page, borrow_transaction, "
                     "fine, payment_order, review_request, notification, outbox_event, "
                     "event_consumer_receipt, idempotency_key IN ACCESS EXCLUSIVE MODE;\n")
        if replace_categories:
            allowed = ", ".join(sql_literal(category) for category in replace_categories)
            stream.write(f"""DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM book_title WHERE category IS NULL OR category NOT IN ({allowed})) THEN
    RAISE EXCEPTION 'Refusing to replace books outside the specified fixture categories';
  END IF;
  IF EXISTS (SELECT 1 FROM fine) OR EXISTS (SELECT 1 FROM payment_order)
     OR EXISTS (SELECT 1 FROM review_request) THEN
    RAISE EXCEPTION 'Fixture replacement requires no fines, payments or reviews';
  END IF;
END $$;
""")
        else:
            stream.write("DO $$ BEGIN IF EXISTS (SELECT 1 FROM book_title) THEN\n"
                         "RAISE EXCEPTION 'The catalog is not empty'; END IF; END $$;\n")
        stream.write("""CREATE TEMP TABLE corpus_before AS
SELECT (SELECT count(*) FROM book_title) AS titles,
       (SELECT count(*) FROM book_copy) AS copies,
       (SELECT count(*) FROM borrow_transaction) AS transactions;
CREATE TEMP TABLE corpus_old_tx AS SELECT id FROM borrow_transaction;
CREATE TEMP TABLE corpus_old_events AS
SELECT id FROM outbox_event WHERE agg_type='CIRCULATION_TX'
AND agg_id IN (SELECT id FROM corpus_old_tx);
DELETE FROM notification WHERE biz_ref IN (SELECT id FROM corpus_old_tx);
DELETE FROM event_consumer_receipt WHERE event_id IN (SELECT id FROM corpus_old_events);
DELETE FROM outbox_event WHERE id IN (SELECT id FROM corpus_old_events);
DELETE FROM idempotency_key
WHERE jsonb_path_exists(response_snapshot, '$.**.txId')
   OR jsonb_path_exists(response_snapshot, '$.**.titleId')
   OR jsonb_path_exists(response_snapshot, '$.**.copyId');
DELETE FROM borrow_transaction;
DELETE FROM book_title_content_page;
DELETE FROM book_copy;
DELETE FROM book_title;
CREATE TEMP TABLE corpus_books (
  source_id bigint PRIMARY KEY, isbn varchar(20), title varchar(256) NOT NULL,
  author varchar(256), publisher varchar(128), category varchar(64),
  publish_year smallint, price_cents bigint NOT NULL, text_bytes bigint,
  text_sha256 text, page_count integer, copy_count integer,
  title_id bigint DEFAULT nextval(pg_get_serial_sequence('book_title', 'id'))
);
COPY corpus_books (source_id, isbn, title, author, publisher, category, publish_year,
                   price_cents, text_bytes, text_sha256, page_count, copy_count) FROM STDIN;
""")
        keys = ("source_id", "isbn", "title", "author", "publisher", "category", "publish_year",
                "price_cents", "text_bytes", "text_sha256", "page_count", "copy_count")
        for book in books:
            stream.write("\t".join(copy_cell(book[key]) for key in keys) + "\n")
        stream.write("\\.\nINSERT INTO book_title (id, isbn, title, author, publisher, category, "
                     "publish_year, price_cents) OVERRIDING SYSTEM VALUE\n"
                     "SELECT title_id, isbn, title, author, publisher, category, publish_year, "
                     "price_cents FROM corpus_books ORDER BY source_id;\n"
                     "INSERT INTO book_copy (title_id, copy_no, status, loan_eligible_on)\n"
                     "SELECT title_id, 'C' || lpad(g::text, 3, '0'), 'SHELF_READY', "
                     "(now() AT TIME ZONE 'Asia/Shanghai')::date\n"
                     "FROM corpus_books, LATERAL generate_series(1, copy_count) g;\n"
                     "CREATE TEMP TABLE corpus_pages (source_id bigint, page_no integer, text text);\n"
                     "COPY corpus_pages (source_id, page_no, text) FROM STDIN;\n")
        for book in books:
            raw = book["text_path"].read_bytes()
            if hashlib.sha256(raw).hexdigest() != book["text_sha256"]:
                raise ValueError(f"Book {book['source_id']}: text changed during rendering.")
            for number, page in enumerate(text_pages(raw.decode("utf-8"), page_chars), 1):
                stream.write(f"{book['source_id']}\t{number}\t{copy_cell(page)}\n")
        stream.write(r"""\.
INSERT INTO book_title_content_page (title_id, page_no, content_type, text)
SELECT b.title_id, p.page_no, 'text/plain', p.text
FROM corpus_pages p JOIN corpus_books b USING (source_id);
DO $$ BEGIN
  IF EXISTS (
    SELECT 1 FROM corpus_books b
    LEFT JOIN (
      SELECT title_id, count(*) AS pages, min(page_no) AS first_page, max(page_no) AS last_page,
             octet_length(string_agg(text, '' ORDER BY page_no)) AS bytes,
             encode(sha256(convert_to(string_agg(text, '' ORDER BY page_no), 'UTF8')), 'hex') AS hash
      FROM book_title_content_page GROUP BY title_id
    ) p USING (title_id)
    WHERE p.pages IS DISTINCT FROM b.page_count OR p.first_page IS DISTINCT FROM 1
       OR p.last_page IS DISTINCT FROM b.page_count OR p.bytes IS DISTINCT FROM b.text_bytes
       OR p.hash IS DISTINCT FROM b.text_sha256
  ) THEN RAISE EXCEPTION 'Imported full text differs from the source'; END IF;
  IF EXISTS (
    SELECT 1 FROM corpus_books b LEFT JOIN (
      SELECT title_id, count(*) AS copies FROM book_copy
      WHERE status='SHELF_READY' GROUP BY title_id
    ) c USING (title_id) WHERE c.copies IS DISTINCT FROM b.copy_count
  ) THEN RAISE EXCEPTION 'Imported copy count differs from the plan'; END IF;
END $$;
COMMIT;
SELECT json_build_object('before', (SELECT row_to_json(b) FROM corpus_before b),
  'titles', (SELECT count(*) FROM corpus_books),
  'copies', (SELECT sum(copy_count) FROM corpus_books),
  'pages', (SELECT sum(page_count) FROM corpus_books),
  'mapping', (SELECT json_agg(json_build_object('source_id', source_id,
     'title_id', title_id) ORDER BY source_id) FROM corpus_books));
""")
    with sql_path.open("rb") as sql_file:
        sql_hash = hashlib.file_digest(sql_file, "sha256").hexdigest()
    plan = dict(checksums, schema="book-corpus-import/1", page_chars=page_chars,
                replace_categories=replace_categories, titles=len(books),
                copies=sum(book["copy_count"] for book in books),
                pages=sum(book["page_count"] for book in books),
                text_bytes=sum(book["text_bytes"] for book in books),
                languages=dict(Counter(book["language"] for book in books)),
                sql_sha256=sql_hash)
    (output / "plan.json").write_text(json.dumps(plan, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return plan


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--corpus", type=Path, default=Path("data/book-corpus"))
    parser.add_argument("--out", type=Path, required=True, help="A new output directory")
    parser.add_argument("--replace-category", action="append", default=[],
                        help="Replace only a catalog made entirely of these fixture categories")
    parser.add_argument("--page-chars", type=int, default=4096)
    args = parser.parse_args()
    plan = render(args.corpus, args.out, args.replace_category, args.page_chars)
    print(json.dumps(plan, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

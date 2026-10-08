#!/usr/bin/env python3
"""Render an atomic, repeatable append of modern metadata and PDF reading text.

This command only writes local files. Apply import.sql after a verified database
backup. Existing titles, copies, transactions and reading pages are preserved.
"""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import subprocess


def digest_file(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def copy_cell(value):
    if value is None:
        return r'\N'
    return str(value).replace('\\', '\\\\').replace('\t', r'\t').replace('\n', r'\n').replace('\r', r'\r')


def validate_isbn(value):
    if not value:
        return None
    value = re.sub(r'[-\s]', '', value).upper()
    if len(value) == 10 and re.fullmatch(r'[0-9]{9}[0-9X]', value):
        if sum((10-index) * (10 if char == 'X' else int(char)) for index, char in enumerate(value)) % 11:
            raise ValueError('Invalid ISBN-10 checksum')
        stem = '978' + value[:9]
        return stem + str((-sum(int(c) * (1 if i % 2 == 0 else 3) for i, c in enumerate(stem))) % 10)
    if len(value) == 13 and value.isascii() and value.isdigit() and value.startswith(('978', '979')):
        if sum(int(c) * (1 if i % 2 == 0 else 3) for i, c in enumerate(value)) % 10 == 0:
            return value
    raise ValueError('Invalid ISBN-13 checksum or format')


def validate_book(book):
    row = dict(book, author='; '.join(book['authors']))
    try:
        row['isbn'] = validate_isbn(book.get('isbn'))
    except ValueError as error:
        row['isbn'] = None
        row['isbn_issue'] = str(error)
        row['original_isbn'] = book.get('isbn')
    for key, limit in [('source', 32), ('source_id', 256), ('isbn', 20), ('title', 256),
                       ('author', 256), ('publisher', 128), ('category', 64), ('summary', None)]:
        value = row.get(key)
        if value is not None and (not isinstance(value, str) or '\x00' in value or (limit and len(value) > limit)):
            raise ValueError(f"{book['source_id']}: invalid {key}")
    if not row['title'].strip() or not row['source'] or not row['source_id']:
        raise ValueError('Missing title or source identity')
    if type(row['price_cents']) is not int or row['price_cents'] < 0:
        raise ValueError('Invalid price')
    if row['publish_year'] is not None and (type(row['publish_year']) is not int or not -32768 <= row['publish_year'] <= 32767):
        raise ValueError('Invalid publication year')
    return row


def split_pdf_pages(text, expected_pages):
    pages = text.split('\f')
    if pages[-1] == '':
        pages.pop()
    if len(pages) != expected_pages or not any(page.strip() for page in pages) or '\x00' in text:
        raise ValueError('PDF extraction has an invalid page count or text')
    return pages


def load_datasets(modern, demo):
    metadata = json.loads((modern/'out/modern-oa-titles.json').read_text(encoding='utf-8'))
    modern_manifest = json.loads((modern/'out/manifest.json').read_text(encoding='utf-8'))
    examples = json.loads((demo/'out/demo-titles.json').read_text(encoding='utf-8'))
    pdf_manifest = json.loads((demo/'out/manifest.json').read_text(encoding='utf-8'))
    if metadata != modern_manifest['books'] or len(metadata) != modern_manifest['selected']:
        raise ValueError('Modern catalog and manifest differ')
    pdfs = {book['id']: book for book in pdf_manifest['items']}
    if len(pdfs) != len(pdf_manifest['items']) or set(pdfs) != {b['source_id'] for b in examples}:
        raise ValueError('Demo catalog and manifest differ')
    rows = [validate_book(book) for book in metadata + examples]
    for key in [lambda b: (b['source'], b['source_id']),
                lambda b: (b['title'].strip().lower(), (b['authors'][0] if b['authors'] else '').strip().lower())]:
        values = [key(book) for book in rows]
        if len(values) != len(set(values)):
            raise ValueError('Duplicate source identity or title and first author')
    isbns = [book['isbn'] for book in rows if book['isbn']]
    if len(isbns) != len(set(isbns)):
        raise ValueError('Duplicate ISBN')
    for row in rows:
        row['pages'] = []
        if row['source'] != 'open-demo':
            continue
        source = pdfs[row['source_id']]
        for key, source_key in [('title','title'), ('authors','authors'), ('category','category'),
                                ('publisher','publisher'), ('publish_year','year'),
                                ('price_cents','price_cents'), ('summary','summary'), ('license','license')]:
            if row[key] != source[source_key]:
                raise ValueError(f"{row['source_id']}: {key} differs from PDF manifest")
        path = (demo/source['pdf_file']).resolve()
        if not path.is_relative_to((demo/'pdfs').resolve()):
            raise ValueError('PDF path is outside pdfs')
        if path.stat().st_size != source['pdf_bytes'] or digest_file(path) != source['pdf_sha256']:
            raise ValueError(f"{row['source_id']}: PDF checksum or size differs")
        info = subprocess.run(['pdfinfo', str(path)], capture_output=True, text=True, check=True).stdout
        expected_pages = int(re.search(r'^Pages:\s+(\d+)', info, re.MULTILINE).group(1))
        text = subprocess.run(['pdftotext', '-layout', '-enc', 'UTF-8', str(path), '-'],
                              capture_output=True, check=True).stdout.decode('utf-8')
        row['pages'] = split_pdf_pages(text, expected_pages)
        row['pdf_sha256'] = source['pdf_sha256']
        row['pdf_file'] = source['pdf_file']
        row['text_sha256'] = hashlib.sha256(''.join(row['pages']).encode()).hexdigest()
        if digest_file(path) != source['pdf_sha256']:
            raise ValueError('PDF changed during extraction')
    return rows


def render(modern, demo, output):
    rows = load_datasets(modern.resolve(), demo.resolve())
    output.mkdir(parents=True, exist_ok=False)
    sql = output/'import.sql'
    with sql.open('w', encoding='utf-8', newline='\n') as stream:
        stream.write(r"""\set ON_ERROR_STOP on
BEGIN;
SET LOCAL lock_timeout = '10s';
SET LOCAL client_encoding = 'UTF8';
LOCK TABLE book_title,book_copy,book_title_content_page IN SHARE ROW EXCLUSIVE MODE;
CREATE TEMP TABLE incoming (
 source text,source_id text,isbn varchar(20),title varchar(256),author varchar(256),
 publisher varchar(128),category varchar(64),publish_year smallint,price_cents bigint,
 summary text,page_count integer,text_sha256 text,title_id bigint,is_new boolean DEFAULT false,
 PRIMARY KEY(source,source_id)
);
COPY incoming(source,source_id,isbn,title,author,publisher,category,publish_year,price_cents,summary,page_count,text_sha256) FROM STDIN;
""")
        for row in rows:
            vals = [row.get(key) for key in ['source','source_id','isbn','title','author','publisher',
                                            'category','publish_year','price_cents','summary']]
            vals += [len(row['pages']), row.get('text_sha256')]
            stream.write('\t'.join(copy_cell(value) for value in vals)+'\n')
        stream.write(r"""\.
CREATE TEMP TABLE matching AS
SELECT i.source,i.source_id,t.id
FROM incoming i JOIN book_title t
 ON (i.isbn IS NOT NULL AND regexp_replace(t.isbn,'[-[:space:]]','','g')=i.isbn)
 OR (lower(btrim(t.title))=lower(btrim(i.title))
     AND lower(btrim(split_part(coalesce(t.author,''),'; ',1)))=lower(btrim(split_part(i.author,'; ',1))));
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM matching GROUP BY source,source_id HAVING count(*)>1)
 OR EXISTS (SELECT 1 FROM matching GROUP BY id HAVING count(*)>1) THEN
  RAISE EXCEPTION 'Ambiguous ISBN or title/author match; existing data preserved';
 END IF;
END $$;
UPDATE incoming i SET title_id=m.id FROM matching m WHERE i.source=m.source AND i.source_id=m.source_id;
UPDATE incoming SET title_id=nextval(pg_get_serial_sequence('book_title','id')),is_new=true WHERE title_id IS NULL;
INSERT INTO book_title(id,isbn,title,author,publisher,category,publish_year,price_cents,summary) OVERRIDING SYSTEM VALUE
SELECT title_id,isbn,title,author,publisher,category,publish_year,price_cents,summary FROM incoming WHERE is_new;
INSERT INTO book_copy(title_id,copy_no,status,loan_eligible_on)
SELECT title_id,'C'||lpad(n::text,3,'0'),'SHELF_READY',(now() AT TIME ZONE 'Asia/Shanghai')::date
FROM incoming,LATERAL generate_series(1,1+(title_id%3)::int) n WHERE is_new;
CREATE TEMP TABLE incoming_pages(source text,source_id text,page_no integer,text text);
COPY incoming_pages(source,source_id,page_no,text) FROM STDIN;
""")
        for row in rows:
            for number, page in enumerate(row['pages'],1):
                stream.write('\t'.join(copy_cell(x) for x in [row['source'],row['source_id'],number,page])+'\n')
        stream.write(r"""\.
CREATE TEMP TABLE reading_added AS SELECT i.title_id FROM incoming i WHERE page_count>0
AND NOT EXISTS(SELECT 1 FROM book_title_content_page p WHERE p.title_id=i.title_id);
INSERT INTO book_title_content_page(title_id,page_no,content_type,text)
SELECT i.title_id,p.page_no,'text/plain',p.text FROM incoming_pages p
JOIN incoming i USING(source,source_id) JOIN reading_added a ON a.title_id=i.title_id;
DO $$ BEGIN
 IF EXISTS (
  SELECT 1 FROM incoming i LEFT JOIN (
   SELECT p.title_id,count(*) AS pages,min(page_no) AS first_page,max(page_no) AS last_page,
   encode(sha256(convert_to(string_agg(p.text,'' ORDER BY page_no),'UTF8')),'hex') AS hash
   FROM book_title_content_page p JOIN incoming i ON i.title_id=p.title_id WHERE i.page_count>0 GROUP BY p.title_id
  ) actual USING(title_id)
  WHERE i.page_count>0 AND (actual.pages IS DISTINCT FROM i.page_count
   OR actual.first_page IS DISTINCT FROM 1 OR actual.last_page IS DISTINCT FROM i.page_count
   OR actual.hash IS DISTINCT FROM i.text_sha256)
 ) THEN RAISE EXCEPTION 'Reading content conflicts or differs from extracted PDF text'; END IF;
END $$;
COMMIT;
SELECT json_build_object('inserted_titles',(SELECT count(*) FROM incoming WHERE is_new),
 'reused_titles',(SELECT count(*) FROM incoming WHERE NOT is_new),
 'inserted_copies',(SELECT coalesce(sum(1+title_id%3),0) FROM incoming WHERE is_new),
 'inserted_pages',(SELECT count(*) FROM incoming_pages p JOIN incoming i USING(source,source_id)
                    JOIN reading_added a ON a.title_id=i.title_id),
 'mapping',(SELECT json_agg(json_build_object('source',source,'source_id',source_id,'title_id',title_id,'new',is_new) ORDER BY source,source_id) FROM incoming));
""")
    files = [modern/'out/modern-oa-titles.json',modern/'out/manifest.json',demo/'out/demo-titles.json',demo/'out/manifest.json']
    plan = dict(schema='book-dataset-append/1',modern_titles=sum(b['source']=='doab' for b in rows),
                demo_titles=sum(b['source']=='open-demo' for b in rows),
                languages=dict(Counter(b['language'] for b in rows)),
                isbn_titles=sum(bool(b['isbn']) for b in rows),
                isbn_issues=[{k:b[k] for k in ['source_id','title','original_isbn','isbn_issue']}
                             for b in rows if 'isbn_issue' in b],reading_pages=sum(len(b['pages']) for b in rows),
                input_hashes={str(p):digest_file(p) for p in files},sql_sha256=digest_file(sql),
                pdfs=[{k:b[k] for k in ['source_id','title','pdf_file','pdf_sha256','text_sha256']}
                      | {'pages':len(b['pages']),'blank_pages':sum(not p.strip() for p in b['pages'])}
                      for b in rows if b['pages']])
    (output/'plan.json').write_text(json.dumps(plan,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    return plan


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--modern',type=Path,default=Path('data/modern-books'))
    parser.add_argument('--demo',type=Path,default=Path('data/book-demo'))
    parser.add_argument('--out',type=Path,required=True)
    args=parser.parse_args()
    print(json.dumps(render(args.modern,args.demo,args.out),ensure_ascii=False,indent=2))

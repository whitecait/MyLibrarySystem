import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('corpus_import', Path(__file__).with_name('import-book-corpus.py'))
corpus_import = importlib.util.module_from_spec(spec)
spec.loader.exec_module(corpus_import)


class CorpusValidationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.corpus = Path(self.temp.name)
        (self.corpus / 'out').mkdir()
        (self.corpus / 'texts').mkdir()
        self.raw = "中文🙂\r\nQuoted 'title'\t\\N\n\\.\n".encode('utf-8')
        (self.corpus / 'texts/pg1.txt').write_bytes(self.raw)
        self.book = dict(source='gutenberg', source_id=1, isbn=None, title='真实书名',
                         authors=['作者'], category='文学', publisher='Project Gutenberg',
                         publish_year=2000, price_cents=1937)
        self.manifest = dict(with_text=1, books=[dict(
            {key: value for key, value in self.book.items() if key not in ('source', 'source_id')},
            gutenberg_id=1, language='zh', text_file='texts/pg1.txt',
            text_bytes=len(self.raw), text_sha256=hashlib.sha256(self.raw).hexdigest())])
        self.write()

    def write(self):
        (self.corpus / 'out/book-titles.json').write_text(json.dumps([self.book]), encoding='utf-8')
        (self.corpus / 'out/manifest.json').write_text(json.dumps(self.manifest), encoding='utf-8')

    def test_corrupted_text_refuses_before_creating_import(self):
        (self.corpus / 'texts/pg1.txt').write_bytes(self.raw + b'changed')
        output = self.corpus / 'rendered'
        with self.assertRaisesRegex(ValueError, 'checksum or size'):
            corpus_import.render(self.corpus, output, [])
        self.assertFalse(output.exists())

    def test_catalog_manifest_disagreement_refuses(self):
        self.book['price_cents'] = 9000
        self.write()
        with self.assertRaisesRegex(ValueError, 'price_cents differs'):
            corpus_import.load_books(self.corpus, 4096)

    def test_overlong_joined_authors_refuse_instead_of_truncation(self):
        self.book['authors'] = ['a' * 128, 'b' * 128]
        self.manifest['books'][0]['authors'] = self.book['authors']
        self.write()
        with self.assertRaisesRegex(ValueError, 'author exceeds'):
            corpus_import.load_books(self.corpus, 4096)

    def test_path_outside_texts_refuses(self):
        self.manifest['books'][0]['text_file'] = '../outside.txt'
        self.write()
        with self.assertRaisesRegex(ValueError, 'outside the texts directory'):
            corpus_import.load_books(self.corpus, 4096)

    def test_duplicate_source_ids_refuse(self):
        (self.corpus / 'out/book-titles.json').write_text(json.dumps([self.book, self.book]), encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'duplicate source IDs'):
            corpus_import.load_books(self.corpus, 4096)

    def test_verified_utf8_bytes_and_copy_special_characters_are_preserved(self):
        rows, _ = corpus_import.load_books(self.corpus, 4096)
        self.assertEqual(rows[0]['text_bytes'], len(self.raw))
        self.assertEqual(corpus_import.copy_cell('\\N\r\n\t\\.'), r'\\N\r\n\t\\.')
        self.assertEqual(''.join(corpus_import.text_pages(self.raw.decode(), 5)).encode(), self.raw)


if __name__ == '__main__':
    unittest.main()

import importlib.util
from pathlib import Path
import unittest

spec=importlib.util.spec_from_file_location('append_books',Path(__file__).with_name('append-book-datasets.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)


class DatasetValidationTest(unittest.TestCase):
    def book(self, **changes):
        return dict(source='doab',source_id='test',isbn=None,title='图书',authors=['作者'],
                    publisher='出版社',category='文学',summary='摘要',publish_year=2024,
                    price_cents=2000,**changes)

    def test_valid_isbn13_and_equivalent_isbn10(self):
        self.assertEqual(m.validate_isbn('978-0-306-40615-7'),'9780306406157')
        self.assertEqual(m.validate_isbn('0-306-40615-2'),'9780306406157')

    def test_invalid_collected_isbn_is_quarantined_without_losing_the_book(self):
        book=self.book();book['isbn']='9781496226089'
        row=m.validate_book(book)
        self.assertEqual(row['title'],'图书')
        self.assertIsNone(row['isbn'])
        self.assertEqual(row['original_isbn'],'9781496226089')
        self.assertIn('checksum',row['isbn_issue'])

    def test_invalid_field_refuses_instead_of_truncating(self):
        book=self.book();book['authors']=['a'*128,'b'*128]
        with self.assertRaisesRegex(ValueError,'invalid author'):m.validate_book(book)

    def test_blank_pdf_pages_remain_in_the_original_page_order(self):
        self.assertEqual(m.split_pdf_pages('\f中文\n\fEnglish\n\f',3),['','中文\n','English\n'])

    def test_incomplete_or_unreadable_pdf_extraction_refuses(self):
        for text,count in [('page\f',2),('\f\f',2),('page\x00\f',1)]:
            with self.assertRaises(ValueError):m.split_pdf_pages(text,count)

    def test_postgres_copy_escapes_special_text(self):
        self.assertEqual(m.copy_cell('\\N\t\r\n\\.'),r'\\N\t\r\n\\.')
        self.assertEqual(m.copy_cell(None),r'\N')


if __name__=='__main__':unittest.main()

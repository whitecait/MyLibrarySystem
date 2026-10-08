import subprocess
import sys
import tempfile
from pathlib import Path
import unittest

TOOL = Path(__file__).with_name("check-frontend-bundle.py")


class FrontendBundleTest(unittest.TestCase):
    def check(self, content):
        with tempfile.TemporaryDirectory() as temporary:
            bundle = Path(temporary) / "entry.js"
            bundle.write_text(content, encoding="utf-8")
            return subprocess.run(
                [sys.executable, str(TOOL), str(bundle)],
                capture_output=True, text=True,
            )

    def test_localized_application_entry_passes(self):
        result = self.check('const title="公共图书馆";createApp(App).mount("#app");')
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_old_title_without_application_mount_fails(self):
        result = self.check('const title="MyLibrarySystem";')
        self.assertNotEqual(result.returncode, 0)

    def test_html_fallback_with_inline_mount_fails(self):
        result = self.check('<!doctype html><script>createApp(App).mount("#app")</script>')
        self.assertNotEqual(result.returncode, 0)

    def test_empty_bundle_fails(self):
        self.assertNotEqual(self.check("").returncode, 0)

    def test_another_mount_target_fails(self):
        self.assertNotEqual(self.check('createApp(App).mount("#other")').returncode, 0)

    def test_single_quoted_mount_passes(self):
        result = self.check("createApp(App).mount('#app')")
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_formatted_mount_passes(self):
        result = self.check('createApp(App).mount( \n  "#app" \n)')
        self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == "__main__":
    unittest.main()

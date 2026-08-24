import argparse
import unittest

from serve import normalized_api_url


class ApiUrlTest(unittest.TestCase):
    def test_accepts_http_urls_and_removes_trailing_slash(self):
        self.assertEqual("https://demo.example/api", normalized_api_url("https://demo.example/api/"))

    def test_rejects_non_http_urls(self):
        with self.assertRaises(argparse.ArgumentTypeError):
            normalized_api_url("file:///tmp/search")

    def test_rejects_query_strings(self):
        with self.assertRaises(argparse.ArgumentTypeError):
            normalized_api_url("https://demo.example?token=secret")


if __name__ == "__main__":
    unittest.main()

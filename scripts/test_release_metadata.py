import unittest
from release_metadata import check_increase


class ReleaseVersionTest(unittest.TestCase):
    def test_first_stable_release(self):
        check_increase(11, '1.0.0')

    def test_both_numbers_must_increase(self):
        for code, name in [(11, '1.0.1'), (12, '1.0.0'), (10, '0.9.0')]:
            with self.assertRaises(ValueError):
                check_increase(code, name, 11, '1.0.0')
        check_increase(12, '1.0.1', 11, '1.0.0')
        check_increase(13, '1.1.0', 12, '1.0.9')

    def test_development_labels_are_rejected(self):
        for name in ['1.0.0-dev', '1.0', 'v1.0.0']:
            with self.assertRaises(ValueError):
                check_increase(11, name)


if __name__ == '__main__':
    unittest.main()

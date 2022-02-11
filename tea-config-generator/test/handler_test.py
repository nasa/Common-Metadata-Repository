""" Test module """
from unittest import TestCase
import handler as handler


#python3 -m unittest discover -p '*_test.py'
#python -m unittest discover -s ./ -p '*_test.py'
class HandlerTest(TestCase):
    """ Test class to test Utils """
    def test_pretty_indent(self):
        """ Test pretty_indent """

        # this is broken, fix it
        #result = handler.pretty_indent({})
        #self.assertEqual(False, result)

        result = handler.pretty_indent({'headers':{'Cmr-Pretty': 'true'}})
        self.assertEqual(1, result)
        result = handler.pretty_indent({'headers':{'Cmr-Pretty': 'false'}})
        self.assertEqual(None, result)

""" Test module """
from unittest import TestCase
import handler as handler


#python3 -m unittest discover -p '*_test.py'
#python -m unittest discover -s ./ -p '*_test.py'
class HandlerTest(TestCase):
    """ Test class to test Utils """

    def test_lowercase_dictionary(self):
        """ Make sure that dictionaries are standardized correctly """

        expected = {'y':"why", "r":"are", 'u':'you', 'a':'a', 'b':'bee'}

        test = lambda e, g, m : self.assertEqual(e, handler.lowercase_dictionary(g), m)

        test(expected, expected, "All lower case check, nothing should change")
        test(expected,
            {'Y':"why", "R":"are", 'U':'you', 'A':'a', 'B':'bee'},
            "All upper case check, all keys should change")
        test(expected,
            {'Y':"why", "r":"are", 'U':'you', 'A':'a', 'b':'bee'},
            "Some upper, some lower, upper should change")
        test(expected,
            {'Y': 'drop-this', 'y':"why", "r":"are", 'u':'you', 'a':'a', 'b':'bee'},
            "double keys, only one should exist")

    def test_pretty_indent(self):
        """ Test pretty_indent """

        # perform test: expected, given, message
        tester = lambda e, g, m : self.assertEqual(e, handler.pretty_indent(g), m)

        tester(None, None, "No envirnment")
        tester(None, {}, "Blank")
        tester(1, {'headers':{'cmr-pretty': 'true'}}, "lowercase test")
        tester(None, {'headers':{'Cmr-Pretty': 'false'}}, "false test")
        tester(1, {'headers':{'Cmr-Pretty': 'true'}}, "true test")

        tester(None, {'headers':{}}, "empty header")
        tester(None, {'headers':{'Cmr-Pretty':''}}, "empty header")
        #tester(None, {'headers':{'Cmr-Pretty':None}}, "empty header")

        # create dictionary: header value, query value
        env = lambda h,q : {'headers':{'Cmr-Pretty':h},'queryStringParameters':{'pretty':q}}

        tester(None, env('false', 'false'), "00")
        tester(1, env('false', 'true'), "01")
        tester(1, env('true', 'false'), "10")
        tester(1, env('true', 'true'), "11")

        tester(None, env('False', 'False'), "Upper case False check")
        tester(1, env('True', 'True'), "Upper case True check")

        tester(None, env('', 'false'), "empty header")
        tester(None, env('false', ''), "empty header")

        tester(1, env('', 'true'), "blank header")
        #tester(1, env('true', ''), "blank param")

#!/usr/bin/env python3

# pylint template.py
# count lines of code with:
# sed -n '/^#cloc-start$/,/^#cloc-end$/p' template.py | cloc - --stdin-name=template.py

#cloc-start

# template modified 2021-09-02

""" template script for future scripts """

#mark - Imports

from enum import Enum   #Creating Enums

# ##############################################################################
#mark - Utility functions - Leave these alone

class VMode(Enum):
    """ Verbose Mode Enums, higher values print less"""
    FATAL = -8 # always print
    ERROR = 0
    NORMAL = 1
    WARN = 2
    INFO = 4
    DEBUG = 8

class TerminalCode(dict):
    """
    A custom dictionary for storing terminal color codes that allows for value
    reuse. To add a duplicate value, prefix the key name with an arrow as the
    value for the new entry
    """
    def __init__(self, data):
        """
        Take in a dictionary, but look at values and decided if they should be
        reused
        """
        super().__init__()
        for key, value in data.items():
            self[key] = self[value[2:]] if value.startswith('->') else value

    def __getattr__ (self, attr):
        """ Allow items to be access with dot notation. """
        return self.get(attr, '\033[0m')

tcode = TerminalCode({'red':'\033[0;31m',
    'green': '\033[0;32m',
    'yellow': '\033[0;33m',
    'blue': '\033[0;34m',
    'white': '\033[0;37m',
    'bold': '\033[1m',
    'underline': '\033[4m',
    #'nc': '\033[0m', # No Color
    # now define duplicates
    VMode.FATAL: '->red',
    VMode.ERROR: '->red',
    VMode.NORMAL: '->white',
    VMode.WARN: '->yellow',
    VMode.INFO: '->blue',
    VMode.DEBUG: '->underline'
})

def is_verbose(environment, verbose):
    """ True if the environment is in verbose mode; Can print in verbose mode """
    return verbose.value <= environment.get("verbose", VMode.WARN).value

def cprint(color:str, content:str, environment:dict=None, verbose:VMode=VMode.NORMAL):
    """
    Color Print, print out text in the requested color, but respect the verbose
    and color modes of the environment variable.

    Responds to ENV:
    * color - True for color (default), False for standard
    * verbose - print verbose mode

    Parameters:
    * color - terminal color code
    * content - text to print out
    * environment - optional environment dictionary
    * verbose - print verbose mode for content (default is NORMAL)

    Return: None
    """
    if environment is None:
    	environment = {}
    if is_verbose(environment, verbose):
        if environment.get("color", True):
            print ("{}{}{}".format(color, content, tcode.nc))
        else:
            print ("{}".format(content))

def vcprint(verbose:VMode, content:str, environment:dict=None):
    """
    Verbose Color Printing:
    Decided what color to print out for the user, based on verbose level

    Parameters:
    * verbose - level for print context
    * content - text to print out
    * environment - application settings
    """
    cprint (tcode.get(verbose, tcode.white), content, environment, verbose)


#!/usr/bin/env python3

# pylint template.py
# count lines of code with:
# sed -n '/^#cloc-start$/,/^#cloc-end$/p' template.py | cloc - --stdin-name=template.py

#cloc-start

# template modified 2021-09-02

""" template script for future scripts """

#mark - Imports

#from enum import Enum   #Creating Enums
#import argparse         #command line parsing
#import code             #interactive shell
#import os               #file handling
#import re               #filter in man()
#import readline         #interactive shell
import subprocess       #calling unix commands
#import sys              #exiting

# ######################################
#mark calling unix commands

def call(*command):
    """ take a list of parameters to call on from unix """
    result = subprocess.run(command, stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT, check=True) # use check?
    return result.stdout.decode('utf-8')

def pipe(*cmd_lists):
    """
    Call multiple commands and pipe the output from one to the next.
    example: pipe(['ps', '-ef'], ['grep', 'Applications'], ['head', '-n', '5'])
    input: a variable number of lists, each list is one command and parameters
    output: the UTF-8 text output of the final command
    """
    pipe_result = None
    for cmd in cmd_lists:
        if pipe_result is None:
            pipe_result = subprocess.run(cmd, check=True, capture_output=True)
        else:
            pipe_result = subprocess.run(cmd, input=pipe_result.stdout,
                capture_output=True, check=True) # use check?
            #print (f"return:{pipe_result.returncode}")
    return pipe_result.stdout.decode('utf-8')

def curl(*flags):
    """ Build a curl command list which can be passed to pipe() or call() """
    cmd = ["curl", "-s", '-A', 'tcherry-script']
    cmd.extend(flags)
    return cmd

def ccurl(*flags):
    """ Run the curl command with some predefined attributes """
    cmd = curl(*flags)
    return call(*cmd)

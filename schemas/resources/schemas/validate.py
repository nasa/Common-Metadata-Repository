"""
This is not a self contain test, but a quick and dirty tool to run some tests.
To run this test, check out and compile the code at
https://git.earthdata.nasa.gov/projects/EMFD/repos/unified-metadata-model-validator/browse
and place this jar file in "~/bin/draft-7-validator.jar". Additionally install
the jq command using `brew install jq`.
"""

import os
import subprocess

JAR = "~/bin/draft-7-validator.jar"

def validate_json(json_file):
  "Check that a JSON file is valid according to the jq command."
  print(f"JSON Syntax for {json_file}\n")
  cmd = ['jq', "--monochrome-output", "--ascii-output", "\".\"", json_file]
  result = subprocess.run(cmd, stdout=subprocess.PIPE)
  print(result.stdout.decode('utf-8'))
  return result.returncode

def validate_schema(jar, schema, metadata):
  "Check that a metadata file conforms to a schema file"
  cmd = ['java', '-jar', jar, schema, metadata]
  result = subprocess.run(cmd, stdout=subprocess.PIPE)
  print(result.stdout.decode('utf-8'))
  return result.returncode

def test_index(schema_name):
  "Check that all versions of index files confirms to a fixed index schema"
  jar = os.path.expanduser(JAR)
  ret = 0
  sub_folders = [f.path for f in os.scandir(schema_name) if f.is_dir()]
  for version in list(sub_folders):
    index_schema = f"{os.getcwd()}/index/v0.0.1/schema.json"
    index = f"{os.getcwd()}/{version}/index.json"
    ret = ret + validate_schema(jar, index_schema, index)
  return ret

def test_schema(schema_name):
  "Check that all versions of a schema matadata matches the definition"
  jar = os.path.expanduser(JAR)
  ret = 0
  sub_folders = [f.path for f in os.scandir(schema_name) if f.is_dir()]
  for version in list(sub_folders):
    print ('*'*80 + "\n")
    schema = f"{os.getcwd()}/{version}/schema.json"
    metadata = f"{os.getcwd()}/{version}/metadata.json"
    ret = ret + validate_schema(jar, schema, metadata)
    ret = ret + validate_json(schema)
    ret = ret + validate_json(metadata)
  return ret

def main():
  ret = test_schema("index")

  for i in ["data-quality-summary", "grid", "order-option", "service-entry", "service-option"]:
    ret = ret + test_schema(i)
    ret = ret + test_index(i)

  if ret == 0:
    print ("No errors found")

if __name__ == "__main__":
  main()

# Testing

In order to run the Python unit tests,
```
cd path/to/CMR/job-utilities
python3 -m unittest test.test_module.test_file.TestClass -v
```
As an example, to run the deploy_schedule_test.py unit tests,
```
python3 -m unittest test.eventbridge_schedule.deploy_schedule_test.TestDeploySchedule -v
```
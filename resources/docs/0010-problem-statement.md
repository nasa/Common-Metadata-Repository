#  • Problem Statement

A culmination of continually increasing code base size, growing number of
dependencies, services that don't stop or restart properly, and other issues
have resulted in CMR development REPL restart times of increasing length. When
several issue act simultaneously, this may result in total turn-around times
from test to restart(s) to re-test of 30 minutes or more.

As such, it has become increasingly important to reduce the time taken for the
following:

* initial startup of the REPL
* restart of CMR services (or removal of their blocking behaviour)
* reloading of code

Benefits of these changes will be a vastly improved CMR developer efficiency,
with hours per week spent in REPL restarts and code/service reloading reduced
to mere minutes per week.

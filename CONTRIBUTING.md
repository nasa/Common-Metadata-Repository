# Making Changes

Thanks for contributing!

To allow us to incorporate your changes, please use the following process:

1. Fork this repository to your personal account.
2. Create a branch and make your changes.
3. Test the changes locally/in your personal fork.
4. Submit a pull request to open a discussion about your proposed changes.
5. The maintainers will talk with you about it and decide to merge or request additional changes.

For general tips on open source contributions, see [Contributing to Open Source on GitHub](https://guides.github.com/activities/contributing-to-open-source/).

# CMR Contribution Guidelines

## Code Formatting and Style
We do our best to ensure that the CMR is readable and easily maintainable, so in addition to adhering to good functional practice, we use these two style guides.
- For general Clojure style: https://github.com/bbatsov/clojure-style-guide
- For namespacing: https://stuartsierra.com/2016/clojure-how-to-ns.html

## Testing
The CMR employs both unit testing, and integration testing. Unit tests should test small areas of code, preferably single functions, whereas integration tests should test the CMR through the API.

- Unit Testing
  - These follow the standard Leiningen test format, in addition to using clojure.test
  - Pure functions are functions that modify no external state or rely on external state to execute. They operate only on the data passed in as arguments and return new data. These are the easiest kinds of functions to test
  - Mocking is hard to get right, makes test brittle, and often misses testing crucial things
  - For more reading:
    - https://github.com/technomancy/leiningen/blob/master/doc/TUTORIAL.md#tests
    - https://clojure.github.io/clojure/clojure.test-api.html

- Integration Testing
  - The state before each integration test is reset using a fixture that sends a request to reset the application to its initial state.
  - Integration tests should test an application or the whole system as a black box. They shouldn't manipulate underlying resources used by the application.

- Which to use, unit or integration test?
  - Test as much as possible at the unit test level. Unit tests are easier to write, run faster, and will be narrowly focused so errors are found more easily.
  - Unit Tests are best for
    - testing pure logic like data manipulation or data conversion.
    - testing single functions.
  - Integration Tests are best for
    - testing integration of components in an application.
    - testing integration of applications in a system.

# Dependencies
The Common Metadata Repository is built using Clojure 1.8.0, with dependencies managed by Leiningen (version 2.5.1 or greater).
  - This also requires Java 1.8.0 or higher
  - We use Mac OSX 10.8 or greater and have had success installing Leiningen via Homebrew and the install script on the Leiningen website

Development and testing can be easily accomplished without the need to set up external Elasticsearch or OracleDB VMs.
The current in-memory configuration is the default and often preferred way to work on the CMR, as it is the easiest configuration to use, and will be sufficient in a large majority of cases.


# License
The Common Metadata Repository is licensed under an Apache 2.0 license as described in
the LICENSE file at the root of the project:

> Copyright © 2021 United States Government as represented by the Administrator of the National Aeronautics and Space Administration. All Rights Reserved.
>
> Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
> You may obtain a copy of the License at
>
>     http://www.apache.org/licenses/LICENSE-2.0
>
> Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
> WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.

By submitting a pull request, you are agreeing to allow distribution
of your work under the above copyright and license.

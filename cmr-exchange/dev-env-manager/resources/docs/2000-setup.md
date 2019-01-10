# Setup

1. Ensure that you have the Common Metadata Repository code base cloned to the
   same directory that the development environment manager is cloned to:
    ```
    $ git clone git@github.com:nasa/Common-Metadata-Repository cmr
    $ git clone git@github.com:cmr-exchange/dev-env-manager cmr-dev-env-manager
    ```
1. Go into the cloned `cmr` directory, and set up Oracle libs (see the `README`
   in `cmr/oracle-lib`).
1. For now, you will need to use a CMR branch that's been fixed up and hasn't
   been merged to master:
     1. `git remote add oubiwann git@github.com:oubiwann/Common-Metadata-Repository.git`
     1. `git checkout oubiwann feature/CMR-4590-support-faster-dev-env`
1. From the `cmr` directory, run `lein install-with-content!`.
1. Change to the `cmr-dev-env-manager` directory.

# Non-UMM ESDIS Schema files

Currently files used by the "Generic Documents" (working title) prototype in CMR
Original Repository: [origin][origin].

## Projects

| Schema             | Implementation | Notes |
| ------------------ | -------------- | ----- |
| Grid               | In progress    | Grid definitions |
| Index              | In progress    | Configuration file for Schema used to specify indexes for Generic Documents |
| OrderOption        | In progress    | Configuration files needed for supporting Legacy Services migration
| DataQualitySummary | In progress    | Configuration files needed for supporting Legacy Services migration
| ServiceEntry       | In progress    | Configuration files needed for supporting Legacy Services migration
| ServiceOption      | In progress    | Configuration files needed for supporting Legacy Services migration

## Usage

These files are to be copied to the [cmr][cmr] repository under the `schemas`
directory when changed so that the files can be used by that software.

## License

Copyright © 2022-2022 United States Government as represented by the
Administrator of the National Aeronautics and Space Administration. All Rights
Reserved.

[origin]: https://git.earthdata.nasa.gov/projects/EMFD/repos/otherschemas "The repository for Generic Documents"
[cmr]: https://github.com/nasa/Common-Metadata-Repository "CMR Git Repository"
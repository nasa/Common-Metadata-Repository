# Non-UMM ESDIS Schema files

Currently files used by the "Generic Documents" (working title) prototype in CMR
Original Repository: [origin][origin].

## Projects

| Schema               | Status    | Notes |
| -------------------- | --------- | ----- |
| Data-Quality-Summary | Active    | Configuration files needed for supporting Legacy Services migration
| Grid                 | Active    | Grid definitions
| Index                | Active    | Configuration file for Schema used to specify indexes for Generic Documents
| Order-Option         | Active    | Configuration files needed for supporting Legacy Services migration
| Provider             | Active    | Definition of a Provider in CMR
| Service-Entry        | Active    | Configuration files needed for supporting Legacy Services migration
| Service-Option       | Active    | Configuration files needed for supporting Legacy Services migration
| *-draft              | CMR only  | Draft MMT formats which are not in the EMFD repository

## Usage

These files are to be copied to the [cmr][cmr] repository under the `schemas`
directory when changed so that the files can be used by that software. The schemas
ending in `-draft` can be ignored, not copied to the [EMDF][origin] repository.

## License

Copyright © 2022-2024 United States Government as represented by the
Administrator of the National Aeronautics and Space Administration. All Rights
Reserved.

[origin]: https://git.earthdata.nasa.gov/projects/EMFD/repos/otherschemas "The repository for Generic Documents"
[cmr]: https://github.com/nasa/Common-Metadata-Repository "CMR Git Repository"

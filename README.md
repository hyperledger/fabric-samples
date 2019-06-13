[//]: # (SPDX-License-Identifier: CC-BY-4.0)

## Hyperledger Fabric Samples

Please visit the [installation instructions](http://hyperledger-fabric.readthedocs.io/en/latest/install.html)
to ensure you have the correct prerequisites installed. Please use the
version of the documentation that matches the version of the software you
intend to use to ensure alignment.

## Download Binaries and Docker Images

The `scripts/bootstrap.sh` (available in the fabric repository)
script will preload all of the requisite docker
images for Hyperledger Fabric and tag them with the 'latest' tag. Optionally,
specify a version for fabric, fabric-ca and thirdparty images. Default versions
are 1.4.1, 1.4.1 and 0.4.15 respectively.


```bash
# Fetch bootstrap.sh from fabric repository using
curl -sS https://raw.githubusercontent.com/hyperledger/fabric/master/scripts/bootstrap.sh -o ./scripts/bootstrap.sh
# Change file mode to executable
chmod +x ./scripts/bootstrap.sh
# Download binaries and docker images (bypass fabric-samples repo clone)
./scripts/bootstrap.sh [version] [ca version] [thirdparty_version] -s
```

### Continuous Integration

Please have a look at [Continuous Integration Process](docs/fabric-samples-ci.md)

## License <a name="license"></a>

Hyperledger Project source code files are made available under the Apache
License, Version 2.0 (Apache-2.0), located in the [LICENSE](LICENSE) file.
Hyperledger Project documentation files are made available under the Creative
Commons Attribution 4.0 International License (CC-BY-4.0), available at http://creativecommons.org/licenses/by/4.0/.

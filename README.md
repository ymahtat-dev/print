[![Maven Package upon a push](https://github.com/mosip/print/actions/workflows/push_trigger.yml/badge.svg?branch=release-1.2.0)](https://github.com/mosip/print/actions/workflows/push_trigger.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?branch=release-1.2.0&project=mosip_admin-services&id=mosip_admin-services&metric=alert_status)](https://sonarcloud.io/dashboard?branch=release-1.2.0&id=mosip_admin-services)
# Print Service

## Overview
A reference implementation to print `euin`, `reprint`, `qrcode` [credential types](https://docs.mosip.io/1.2.0/modules/id-repository#credential-types) in PDF format. This service is intended to be customized and used by a card printing agency who need to onboard onto MOSIP as [Credential Partner](https://docs.mosip.io/1.2.0/partners#credential-partner-cp) before deploying the service.  

![](docs/print-service.png)

1. Receives events from WebSub.
2. Fetches templates from Masterdata.
3. After creating PDF card print service upload the same to [DataShare](https://docs.mosip.io/1.2.0/modules/data-share).
4. Publishes event to WebSub with updated status and DataShare link.

The card data in JSON format is published as WebSub event.  The print service consumes the data from event, decrypts using partner private key and converts into PDF using a predefined [template](docs/configuration.md#template).

## Build and run (for developers)
Refer [Build and Run](docs/build-and-run.md).
    
## Deploy
To deploy print service in production follow the given steps:

1. Onboard your organisation as [Credential Partner](https://docs.mosip.io/1.2.0/partners).
2. Place your `.p12` file in `../src/main/resources` folder.
3. Set configuration as in given [here](docs/configuation.md).
4. Build and run as given [here](docs/build-and-run.md).

## Configuration
Refer to the [configuration guide](docs/configuration.md).

## Test
Automated functional tests available in [Functional Tests repo](https://github.com/mosip/mosip-functional-tests).

## License
This project is licensed under the terms of [Mozilla Public License 2.0](LICENSE).

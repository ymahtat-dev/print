# Print Service Configuration Guide

## Overview
The guide here lists down some of the important properties that may be customised for a given installation. Note that the listing here is not exhaustive, but a checklist to review properties that are likely to be different from default. If you would like to see all the properties, then refer to the files listed below.

## Configuration files
Print service uses the following configuration files:
```
application-default.properties
print-default.properties
registration-processor-print-text-file.json
identity-mapping.json
```
## Configuration
[Configuration-Application](https://github.com/mosip/mosip-config/blob/release-1.3.x/application-default.properties),
[Configuration-Print](https://github.com/mosip/mosip-config/blob/release-1.3.x/print-default.properties),
[Configuration-RegProcPrintTextFile](https://github.com/mosip/mosip-config/blob/release-1.3.x/registration-processor-print-text-file.json) and
[Configuration-IdMapping](https://github.com/mosip/mosip-config/blob/release-1.3.x/identity-mapping.json) defined here.

Refer [Module Configuration](https://docs.mosip.io/1.2.0/modules/module-configuration) for location of these files.

## Template
The HTML template `RPR_UIN_CARD_TEMPLATE` used for printing is present in the master data. You can alter the same for any look and feel change. Key name in master data for template.  The template located in `template` table of `mosip_master` DB.

Specify the language of the template in the following property
```
mosip.template-language=eng
```

Refer to [PrintServiceImpl.java](../src/main/java/io/mosip/print/service/impl/PrintServiceImpl.java) to understand the PDF implementation.

## WebSub
```
mosip.event.hubURL = //WebSub url
mosip.partner.id = //your partner id from partner portal
mosip.event.callBackUrl = //call back url for WebSub so upon a credential issued event the WebSub will call this url. eg: https://dev.mosip.net/v1/print/print/callback/notifyPrint
mosip.event.topic = // event topic
mosip.event.delay-millisecs = // subscription delay time. 
print-WebSub-resubscription-delay-millisecs = // resubscription delay time.
mosip.event.secret = //secret key 
```

## DataShare
```
mosip.datashare.partner.id = /your partner id from partner portal
mosip.datashare.policy.id = /your policy id from partner portal
CREATEDATASHARE = //datashare url 
```

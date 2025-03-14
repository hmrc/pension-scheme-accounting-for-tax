# Pension Scheme Accounting For Tax

- [Overview](#overview)
- [Requirements](#requirements)
- [Running the Service](#running-the-service)
- [Enrolments](#enrolments)
- [Compile & Test](#compile--test)
- [Navigation and Dependent Services](#navigation-and-dependent-services)
- [Terminology](#note-on-terminology)
- [Endpoints Used](#endpoints-used)
- [License](#license)

## Overview

This is the backend repository for the Pension Scheme Accounting for Tax service. This back-end microservice supports the bulk uploading of files and amendments to AFT Returns.

This service has a corresponding front-end microservice, namely pension-scheme-accounting-for-tax-frontend.

**Associated Frontend Link:** [https://github.com/hmrc/pension-administrator-frontend](https://github.com/hmrc/pension-scheme-accounting-for-tax-frontend)

**Stubs:** https://github.com/hmrc/pensions-scheme-stubs



## Requirements
This service is written in Scala and Play, so needs at least a [JRE] to run.

**Node version:** 16.20.2

**Java version:** 21

**Scala version:** 2.13.14


## Running the Service
**Service Manager Profile:** PODS_ALL

**Port:** 8205

In order to run the service, ensure Service Manager is installed (see [MDTP guidance](https://docs.tax.service.gov.uk/mdtp-handbook/documentation/developer-set-up/set-up-service-manager.html) if needed) and launch the relevant configuration by typing into the terminal:
`sm2 --start PODS_ALL`

To run the service locally, enter `sm2 --stop PENSION_SCHEME_ACCOUNTING_FOR_TAX`.

In your terminal, navigate to the relevant directory and enter `sbt run`.

Access the Authority Wizard and login with the relevant enrolment details [here](http://localhost:9949/auth-login-stub/gg-sign-in)


## Enrolments
There are several different options for enrolling through the auth login stub. In order to enrol as a dummy user to access the platform for local development and testing purposes, the following details must be entered on the auth login page.


For access to the **Pension Administrator dashboard** for local development, enter the following information: 

**Redirect url -** http://localhost:8204/manage-pension-schemes/overview 

**GNAP Token -** NO 

**Affinity Group -** Organisation 

**Enrolment Key -** HMRC-PODS-ORG 

**Identifier Name -** PsaID 

**Identifier Value -** A2100005

---

If you wish to access the **Pension Practitioner dashboard** for local development, enter the following information: 

**Redirect URL -** http://localhost:8204/manage-pension-schemes/dashboard 

**GNAP Token -** NO 

**Affinity Group -** Organisation 

**Enrolment Key -** HMRC-PODSPP-ORG 

**Identifier Name -** PspID 

**Identifier Value -** 21000005

---

**Dual enrolment** as both a Pension Administrator and Practitioner is also possible and can be accessed by entering:

**Redirect url -** http://localhost:8204/manage-pension-schemes/overview 

**GNAP Token -** NO 

**Affinity Group -** Organisation 

**Enrolment Key 1 -** HMRC-PODSPP-ORG Identifier 

**Name 1 -** PspID Identifier 

**Value 1 -** 21000005

**Enrolment Key 2 -** HMRC-PODS-ORG 

**Identifier Name 2 -** PsaID 

**Identifier Value 2 -** A2100005

---

To access the **Scheme Registration journey**, enter the following information:

**Redirect URL -** http://localhost:8204/manage-pension-schemes/you-need-to-register 

**GNAP Token -** NO 

**Affinity Group -** Organisation

---


## Compile & Test
**To compile:** Run `sbt compile`

**To test:** Use `sbt test`

**To view test results with coverage:** Run `sbt clean coverage test coverageReport`

For further information on the PODS Test Approach and wider testing including acceptance, accessibility, performance, security and E2E testing, visit the PODS Confluence page [here](https://confluence.tools.tax.service.gov.uk/pages/viewpage.action?spaceKey=PODSP&title=PODS+Test+Approach).

For Journey Tests, visit the [Journey Test Repository](| Journey tests(https://github.com/hmrc/pods-journey-tests).

View the prototype [here](https://pods-event-reporting-prototype.herokuapp.com/).


## Navigation and Dependent Services
The Pension Administrator Frontend integrates with the Manage Pension Schemes (MPS) service and uses various stubs available on [GitHub](https://github.com/hmrc/pensions-scheme-stubs). From the Authority Wizard page you will be redirected to the dashboard. Navigate to the appropriate area by accessing items listed within the service-specific tiles on the dashboard. On the Pension Administrator frontend, an administrator can change their details, stop being an administrator and check for invitations, explore Penalties & Charges, manage and migrate pension schemes.


There are numerous APIs implemented throughout the MPS architecture, and the relevant endpoints are illustrated below. For an overview of all PODS APIs, refer to the [PODS API Documentation](https://confluence.tools.tax.service.gov.uk/display/PODSP/PODS+API+Latest+Version).

## Note on terminology
The terms scheme reference number and submission reference number (SRN) are interchangeable within the PODS codebase; some downstream APIs use scheme reference number, some use submission reference number, probably because of oversight on part of the technical teams who developed these APIs. This detail means the same thing, the reference number that was returned from ETMP when the scheme details were submitted.

## Endpoints Used

| *Task*                                                                | *Supported Methods* | *Description*                                                                                                          |
|-----------------------------------------------------------------------|---------------------|------------------------------------------------------------------------------------------------------------------------|
| ```/aft-file-return                                     ```           | POST                | File Accounting For Tax (AFT) Return [More...](docs/aft-file-return.md)                                                |
| ```/get-aft-details                                       ```         | GET                 | Get Accounting For Tax (AFT) Details [More...](docs/get-aft-details.md)                                                |
| ```/get-aft-versions                                     ```          | GET                 | Get PODS Report Versions in a given period [More...](docs/get-aft-versions.md)                                         |
| ```/get-aft-overview                                              ``` | GET                 | Get PODS Report Versions Overview in a given period[More...](docs/get-aft-overview.md)                                 |
| ```/journey-cache/aft               ```                               | GET                 | Returns the data from AFT Cache based on session id, quarter start date and PSTR                                       |    
| ```/journey-cache/aft               ```                               | POST                | Saves the data to AFT Cache with key as the combination of session id, quarter start date and PSTR                     |     
| ```/journey-cache/aft               ```                               | DELETE              | Removes the data from AFT Cache based on session id, quarter start date and PSTR                                       |      
| ```/journey-cache/aft/lock   ```                                      | GET                 | Returns the locked by user name if the data for a given session id, quarter start date and PSTR is locked in AFT Cache |
| ```/journey-cache/aft/lock                     ```                    | POST                | Saves the lock with the name of the user for a given session id, quarter start date and PSTR in AFT Cache              |   

| Service               | HTTP Method | Route                                                     | Purpose                                                                                                                |
|-----------------------|-------------|-----------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------|
| Accounting For Tax    | POST        | /pension-scheme-accounting-for-tax/aft-file-return        | Submits/Updates an AFT Return                                                                                          |
| Accounting For Tax    | GET         | /pension-scheme-accounting-for-tax/get-aft-details        | Returns AFT details                                                                                                    |
| Accounting For Tax    | GET         | /pension-scheme-accounting-for-tax/get-aft-versions       | Returns AFT reporting versions for a given period                                                                      |
| Accounting For Tax    | GET         | /pension-scheme-accounting-for-tax/get-aft-overview       | Returns the overview of the AFT versions in a given period                                                             |
| Accounting For Tax    | GET         | /pension-scheme-accounting-for-tax/journey-cache/aft      | Returns the data from AFT Cache based on session id, quarter start date and PSTR                                       |
| Accounting For Tax    | POST        | /pension-scheme-accounting-for-tax/journey-cache/aft      | Saves the data to AFT Cache with key as the combination of session id, quarter start date and PSTR                     |
| Accounting For Tax    | DELETE      | /pension-scheme-accounting-for-tax/journey-cache/aft      | Removes the data from AFT Cache based on session id, quarter start date and PSTR                                       |
| Accounting For Tax    | GET         | /pension-scheme-accounting-for-tax/journey-cache/aft/lock | Returns the locked by user name if the data for a given session id, quarter start date and PSTR is locked in AFT Cache |
| Accounting For Tax    | POST        | /pension-scheme-accounting-for-tax/journey-cache/aft/lock | Sets the lock with the name of the user for a given session id, quarter start date and PSTR in AFT Cache               |
| Pensions Scheme       | GET         | /pensions-scheme/scheme                                   | Returns details of a scheme                                                                                            |
| Pensions Scheme       | GET         | /pensions-scheme/is-psa-associated                        | Returns true if Psa is associated with the selected scheme                                                             |
| Pension Administrator | GET         | /pension-administrator/get-minimal-psa                    | Returns minimal PSA details                                                                                            | 
| Address Lookup        | GET         | /v2/uk/addresses                                          | Returns a list of addresses that match a given postcode                                                                | 
| Email                 | POST        | /hmrc/email                                               | Sends an email to an email address                                                                                     | 

---

## License
This code is open source software Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at:

[http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.

[↥ Back to Top](#pension-scheme-accounting-for-tax)

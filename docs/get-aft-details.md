Get-aft-details
-----------------------
Returns AFT Details

* **URL**

  `/get-aft-details`

* **Method**

  `GET`

*  **Request Header**
    
   `pstr`,
   `startDate`,
   `aftVersion`

* **Success Response:**

  * **Code:** 200 
  
* **Example Success Response**

```json

{
  "aftStatus": "Compiled",
  "pstr": "1234",
  "schemeName": "Test Scheme",
  "quarter": {
    "startDate": "2019-01-01",
    "endDate": "2019-03-31"
  },
  "chargeADetails": {
    "numberOfMembers": 2,
    "totalAmtOfTaxDueAtLowerRate": 200.02,
    "totalAmtOfTaxDueAtHigherRate": 200.02,
    "totalAmount": 200.02,
    "amendedVersion": 1
  },
  "chargeBDetails": {
    "numberOfDeceased": 2,
    "amountTaxDue": 100.02,
    "amendedVersion": 1
  },
  "chargeCDetails": {
    "employers": [
      {
        "memberStatus": "New",
        "memberAFTVersion": 2,
        "whichTypeOfSponsoringEmployer": "individual",
        "chargeDetails": {
          "paymentDate": "2020-01-01",
          "amountTaxDue": 500.02
        },
        "sponsoringIndividualDetails": {
          "firstName": "testFirst",
          "lastName": "testLast",
          "nino": "AB100100A",
          "isDeleted": false
        },
        "sponsoringEmployerAddress": {
          "line1": "line1",
          "line2": "line2",
          "line3": "line3",
          "line4": "line4",
          "postcode": "NE20 0GG",
          "country": "GB"
        }
      }
    ],
    "totalChargeAmount": 500.02,
    "amendedVersion": 1
  },
  "chargeDDetails": {
    "members": [
      {
        "memberStatus": "Changed",
        "memberAFTVersion": 1,
        "memberDetails": {
          "firstName": "Joy",
          "lastName": "Kenneth",
          "nino": "AA089000A",
          "isDeleted": false
        },
        "chargeDetails": {
          "dateOfEvent": "2016-02-29",
          "taxAt25Percent": 1.02,
          "taxAt55Percent": 9.02
        }
      }
    ],
    "totalChargeAmount": 2345.02,
    "amendedVersion": 1
  },
  "chargeEDetails": {
    "members": [
      {
        "memberStatus": "New",
        "memberAFTVersion": 3,
        "memberDetails": {
          "firstName": "eFirstName",
          "lastName": "eLastName",
          "nino": "AE100100A",
          "isDeleted": false
        },
        "annualAllowanceYear": "2020",
        "chargeDetails": {
          "dateNoticeReceived": "2020-01-11",
          "chargeAmount": 200.02,
          "isPaymentMandatory": true
        }
      }
    ],
    "totalChargeAmount": 200.02,
    "amendedVersion": 1
  },
  "chargeFDetails": {
    "amendedVersion": 1,
    "amountTaxDue": 200.02,
    "deRegistrationDate": "1980-02-29"
  },
  "chargeGDetails": {
    "members": [
      {
        "memberStatus": "Deleted",
        "memberAFTVersion": 1,
        "memberDetails": {
          "firstName": "Craig",
          "lastName": "White",
          "dob": "1980-02-29",
          "nino": "AA012000A",
          "isDeleted": false
        },
        "chargeDetails": {
          "qropsReferenceNumber": "300000",
          "qropsTransferDate": "2016-02-29"
        },
        "chargeAmounts": {
          "amountTransferred": 45670.02,
          "amountTaxDue": 4560.02
        }
      }
    ],
    "totalChargeAmount": 1230.02,
    "amendedVersion": 1
  }
}

```

* **Error Response:**

  * **Code:** 400 BAD_REQUEST <br />
    **Content:** `{
                     "code": "INVALID_PSTR",
                     "reason": "Submission has not passed validation. Invalid parameter pstr."
                  }`

  * **Code:** 404 NOT_FOUND <br />
    **Content:** `{
                     "code": "NO_DATA_FOUND",
                     "reason": "The remote endpoint has indicated that there is no AFT return found."
                  }`
    
  * **Code:** 403 FORBIDDEN <br />
    **Content:** `{
                      "failures": [
                          {
                              "code": "INVALID_PSTR",
                              "reason": "The remote endpoint has indicated that Invalid PSTR."
                          },
                          {
                              "code": "INCORRECT_PERIOD_START_DATE",
                              "reason": "The remote endpoint has indicated that Period Start Date cannot be in the future."
                          }
                      ]
                  }`
    
  * **Code:** 4XX Upstream4xxResponse <br />

  OR anything else

  * **Code:** 5XX Upstream5xxResponse <br />
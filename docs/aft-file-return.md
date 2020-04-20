Aft-file-return
-----------------------
File an AFT Return

* **URL**

  `/aft-file-return`

* **Method**

  `POST`

*  **Request Header**
    
   `pstr`

* **Example Payload**

```json

 {
   "aftStatus": "Compiled",
   "quarter": {
     "startDate": "2019-01-01",
     "endDate": "2019-03-31"
   },
   "chargeADetails": {
     "numberOfMembers": 2,
     "totalAmtOfTaxDueAtLowerRate": 200.02,
     "totalAmtOfTaxDueAtHigherRate": 200.02,
     "totalAmount": 200.02
   },
   "chargeBDetails": {
     "numberOfDeceased": 4,
     "amountTaxDue": 55.55
   },
   "chargeCDetails": {
     "employers": [
       {
         "whichTypeOfSponsoringEmployer": "individual",
         "memberStatus": "Changed",
         "memberAFTVersion": 1,
         "chargeDetails": {
           "paymentDate": "2020-01-01",
           "amountTaxDue": 500.02
         },
         "sponsoringIndividualDetails": {
           "firstName": "testFirst",
           "lastName": "testLast",
           "nino": "AB100100A"
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
     "numberOfMembers": 2,
     "members": [
       {
         "memberDetails": {
           "firstName": "firstName",
           "lastName": "lastName",
           "nino": "AC100100A",
           "isDeleted": false
         },
         "chargeDetails": {
           "dateOfEvent": "2020-01-10",
           "taxAt25Percent": 100,
           "taxAt55Percent": 100.02
         }
       }
     ],
     "totalChargeAmount": 200.02
   },
   "chargeEDetails": {
     "members": [
       {
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
     "totalChargeAmount": 200.02
   },
   "chargeFDetails": {
     "amountTaxDue": 200.02,
     "deRegistrationDate": "1980-02-29"
   },
   "chargeGDetails": {
     "members": [
       {
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
     "totalChargeAmount": 1230.02
   },
   "declaration" : {
     "submittedBy" : "PSA",
     "submittedID" : "A2000000",
     "hasAgreed" : true
   }
 }

```

* **Success Response:**

  * **Code:** 200 <br />

* **Example Success Response**

```json
{
	"processingDate": "2001-12-17T09:30:47Z",
	"formBundleNumber": "123456789012"
}
```

* **Error Response:**

  * **Code:** 400 BAD_REQUEST <br />
    **Content:** `{
                     "code": "AMOUNT_MISMATCH",
                     "reason": "The remote endpoint has indicated that members total amount does not match with the amount of charge type."
                  }`

  * **Code:** 400 BAD_REQUEST <br />
    **Content:** `{
                              "code": "INVALID_PSTR",
                              "reason": "Submission has not passed validation. Invalid parameter pstr."
                          }`
    
  * **Code:** 400 BAD_REQUEST <br />
    **Content:** `{
                              "code": "INVALID_CORRELATIONID",
                              "reason": "Submission has not passed validation. Invalid Header parameter CorrelationId."
                          }`
    
  * **Code:** 4XX Upstream4xxResponse <br />

  OR anything else

  * **Code:** 5XX Upstream5xxResponse <br />
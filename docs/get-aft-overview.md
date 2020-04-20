Get-aft-overview
-----------------------
Returns AFT Versions Overview

* **URL**

  `/get-aft-overview`

* **Method**

  `GET`

*  **Request Header**
    
   `pstr`,
   `startDate`,
   `endDate`

* **Success Response:**

  * **Code:** 200 
  
* **Example Success Response**

```json
[
  {
    "periodStartDate": "2020-04-01",
    "periodEndDate": "2020-06-30",
    "numberOfVersions": 3,
    "submittedVersionAvailable": false,
    "compiledVersionAvailable": true
  },
  {
    "periodStartDate": "2020-07-01",
    "periodEndDate": "2020-10-31",
    "numberOfVersions": 2,
    "submittedVersionAvailable": true,
    "compiledVersionAvailable": true
  }
]
```

* **Error Response:**

  * **Code:** 403 FORBIDDEN <br />
    **Content:** `{
                     "code": "REQUEST_NOT_PROCESSED",
                                       "reason": "The remote endpoint has indicated that request could not be processed."
                  }`
    
  * **Code:** 400 BAD_REQUEST <br />
    **Content:** `{    
                      "failures": [
                                                          {
                                                            "code": "INVALID_PSTR",
                                                            "reason": "Submission has not passed validation. Invalid parameter pstr."
                                                          },
                                                          {
                                                            "code": "INVALID_FROM_DATE",
                                                            "reason": "Submission has not passed validation. Invalid parameter fromDate."
                                                          }
                                                        ]
                  }`
    
  * **Code:** 4XX Upstream4xxResponse <br />

  OR anything else

  * **Code:** 5XX Upstream5xxResponse <br />
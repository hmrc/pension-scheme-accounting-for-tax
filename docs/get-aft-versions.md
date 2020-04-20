Get-aft-versions
-----------------------
Returns AFT Versions

* **URL**

  `/get-aft-versions`

* **Method**

  `GET`

*  **Request Header**
    
   `pstr`
   `startDate`

* **Success Response:**

  * **Code:** 200 
  
* **Example Success Response**

```json

[{"reportVersion":1,"date":"2020-04-20"}]

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
    
  * **Code:** 400 BAD_REQUEST <br />
    **Content:** `{    
                      "failures": [
                  	{
                     	   "code": "INVALID_START_DATE",
                     	   "reason": "Submission has not passed validation. Invalid parameter startDate."
                  	},
                  	{     
                             "code": "INVALID_REPORT_TYPE",
                             "reason": "Submission has not passed validation. Invalid parameter reportType."
                          }
                      ]
                  }`
    
  * **Code:** 4XX Upstream4xxResponse <br />

  OR anything else

  * **Code:** 5XX Upstream5xxResponse <br />
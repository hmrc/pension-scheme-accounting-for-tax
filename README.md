Pensions Scheme Accounting For Tax
==================================

Back-end microservice to support the file or amend an AFT Return

API
---

| *Task* | *Supported Methods* | *Description* |
|--------|----|----|
| ```/aft-file-return                                     ```  | POST   | File Accounting For Tax (AFT) Return [More...](docs/aft-file-return.md) |
| ```/get-aft-details                                       ```  | GET   | Get Accounting For Tax (AFT) Details [More...](docs/get-aft-details.md) |
| ```/get-aft-versions                                     ```  | GET    | Get PODS Report Versions in a given period [More...](docs/get-aft-versions.md) |
| ```/get-aft-overview                                              ```  | GET    | Get PODS Report Versions Overview in a given period[More...](docs/get-aft-overview.md) |
| ```/journey-cache/aft               ```  | GET    | Returns the data from AFT Cache based on the X-Session-ID and ID (the scheme SRN ID) found in the request header
| ```/journey-cache/aft               ```  | POST   | Saves the data in the request body to AFT Cache based on the X-Session-ID and ID (the scheme SRN ID) found in the request header. IF the request header contains the elements "chargeType" and/or "memberNo" then the data saved will only be that relating to the specified charge type and/or (for member-based charges) member number.
| ```/journey-cache/aft               ```  | DELETE | Removes the data from AFT Cache based on the X-Session-ID and ID (the scheme SRN ID) found in the request header
| ```/journey-cache/session-data/aft               ```  | GET    | Returns the data and the session data from AFT Cache based on the X-Session-ID and ID (the scheme SRN ID) found in the request header.
| ```/journey-cache/session-data/aft               ```  | POST   | Saves the data in the request body and also the session data to AFT Cache with key as the combination of X-Session-ID and ID (the scheme SRN ID) found in the request header.
| ```/journey-cache/session-data-lock/aft          ```  | POST   | Saves the data in the request body and also the session data to AFT Cache with key as the combination of X-Session-ID and ID (the scheme SRN ID) found in the request header. Also locks the data to the name and PSA or PSP ID of the logged-in user.
| ```/journey-cache/aft/lock   ```  | GET    | Returns the user name of the PSA/PSP who has the AFT return locked, identified by the X-Session-ID and ID (the scheme SRN ID) found in the request header. If the AFT return is not locked or the AFT return is locked but by the currently logged-in user then it returns a NOT_FOUND HTTP response.  

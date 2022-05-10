Pensions Scheme Accounting For Tax
==================================

Back-end microservice to support the file or amend an AFT Return //TOOO: Clarification on what support the file means?

API
---

// TODO: Clarification on the dynamic parameters?

| *Task* | *Supported Methods* | *Description* |
|--------|----|----|
| ```/aft-file-return                                     ```  | POST   | File Accounting For Tax (AFT) Return [More...](docs/aft-file-return.md) |
| ```/get-aft-details                                       ```  | GET   | Get Accounting For Tax (AFT) Details [More...](docs/get-aft-details.md) |
| ```/get-aft-versions                                     ```  | GET    | Get PODS Report Versions in a given period [More...](docs/get-aft-versions.md) |
| ```/get-aft-overview                                              ```  | GET    | Get PODS Report Versions Overview in a given period[More...](docs/get-aft-overview.md) |
| ```/journey-cache/aft               ```  | GET    | Returns the data from AFT Cache based on session id, quarter start date and PSTR
| ```/journey-cache/aft               ```  | POST   | Saves the data to AFT Cache with key as the combination of session id, quarter start date and PSTR
| ```/journey-cache/aft               ```  | DELETE | Removes the data from AFT Cache based on session id, quarter start date and PSTR
| ```/journey-cache/aft/lock   ```  | GET    | Returns the locked by user name if the data for a given session id, quarter start date and PSTR is locked in AFT Cache
| ```/journey-cache/aft/lock                     ```  | POST    | Saves the lock with the name of the user for a given session id, quarter start date and PSTR in AFT Cache

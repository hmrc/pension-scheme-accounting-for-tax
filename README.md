Pensions Scheme Accounting For Tax
==================================

Back-end microservice to support the file or amend an AFT Return

API
---

| *Task* | *Supported Methods* | *Description* |
|--------|----|----|
| ```/aft-file-return                                     ```  | POST   | File AFT Return [More...](docs/aft-file-return.md) |
| ```/get-aft-details                                       ```  | GET   | Get AFT Details [More...](docs/get-aft-details.md) |
| ```/get-aft-versions                                     ```  | GET    | Get AFT Versions [More...](docs/get-aft-versions.md) |
| ```/get-aft-overview                                              ```  | GET    | Get AFT Overview [More...](docs/get-aft-overview.md) |
| ```/journey-cache/aft               ```  | GET    | Returns the data from AFT Cache 
| ```/journey-cache/aft               ```  | POST   | Saves the data to AFT Cache
| ```/journey-cache/aft               ```  | DELETE | Removes the data from AFT Cache
| ```/journey-cache/aft/lock   ```  | GET    | Returns if the data for a selected Id is locked in AFT Cache
| ```/journey-cache/aft/lock                     ```  | POST    | Saves the lock in AFT Cache

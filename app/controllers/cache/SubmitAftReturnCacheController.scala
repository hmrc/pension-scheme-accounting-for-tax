/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers.cache

import com.google.inject.Inject
import play.api.mvc._
import repository.SubmitAftReturnCacheRepository
import repository.SubmitAftReturnCacheRepository.SubmitAftReturnCacheEntry
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext

class SubmitAftReturnCacheController @Inject()(
                                                repository: SubmitAftReturnCacheRepository,
                                                val authConnector: AuthConnector,
                                                cc: ControllerComponents
                                           )(implicit ec: ExecutionContext) extends BackendController(cc) with AuthorisedFunctions {

  def post: Action[AnyContent] = Action.async {
    _ =>
      repository.insertLockData(SubmitAftReturnCacheEntry("123", "testUser", "2021-02-02", "001")).map {
        case true => Ok
        case false => NoContent
      }
  }
}







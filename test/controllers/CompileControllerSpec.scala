/*
 * Copyright 2019 HM Revenue & Customs
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

package controllers

import com.google.inject.Injector
import config.AppConfig
import org.scalatest.{Matchers, WordSpec}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.{Application, Configuration, Environment, Mode}
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.{FakeRequest, Helpers}
import play.api.test.Helpers._
import uk.gov.hmrc.play.bootstrap.config.{RunMode, ServicesConfig}

class CompileControllerSpec extends WordSpec with Matchers with GuiceOneAppPerSuite {

  val injector = app.injector
  val fakeRequest = FakeRequest("GET", "/")

  val env: Environment = Environment.simple()
  val configuration: Configuration = Configuration.load(env)

  val serviceConfig = new ServicesConfig(configuration, new RunMode(configuration, Mode.Dev))
  def appConfig: AppConfig = injector.instanceOf[AppConfig]

  private val controller = new CompileController(appConfig, Helpers.stubControllerComponents())

  "Compile" should {
    "return 200" in {
      val jsonBody = Json.obj()
      val result = controller.fileReturn()(fakeRequest.withJsonBody(jsonBody).withHeaders("pstr"->""))
      status(result) shouldBe Status.OK
    }
  }
}

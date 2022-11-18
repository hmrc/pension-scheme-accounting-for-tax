/*
 * Copyright 2022 HM Revenue & Customs
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

package services

import akka.Done
import base.SpecBase
import models.FeatureToggle.{Disabled, Enabled}
import models.FeatureToggleName._
import models.{FeatureToggle, FeatureToggleName}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.cache.AsyncCacheApi
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import repository._

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.reflect.ClassTag

class FeatureToggleServiceSpec
  extends SpecBase
    with MockitoSugar
    with ScalaFutures
    with Matchers {

  val adminDataRepository: AdminDataRepository = mock[AdminDataRepository]

  implicit private val arbitraryFeatureToggleName: Arbitrary[FeatureToggleName] =
    Arbitrary {
      Gen.oneOf(FeatureToggleName.toggles)
    }

  override protected def bindings: Seq[GuiceableModule] = {
    Seq(
      bind[AdminDataRepository].toInstance(adminDataRepository),
      bind[AftBatchedDataCacheRepository].toInstance(mock[AftBatchedDataCacheRepository]),
      bind[AftOverviewCacheRepository].toInstance(mock[AftOverviewCacheRepository]),
      bind[FileUploadReferenceCacheRepository].toInstance(mock[FileUploadReferenceCacheRepository]),
      bind[FileUploadOutcomeRepository].toInstance(mock[FileUploadOutcomeRepository]),
      bind[FinancialInfoCacheRepository].toInstance(mock[FinancialInfoCacheRepository]),
      bind[FinancialInfoCreditAccessRepository].toInstance(mock[FinancialInfoCreditAccessRepository]),
      bind[AsyncCacheApi].toInstance(new FakeCache())
    )
  }

  // A cache that doesn't cache
  class FakeCache extends AsyncCacheApi {
    override def set(key: String, value: Any, expiration: Duration): Future[Done] = ???

    override def remove(key: String): Future[Done] = ???

    override def getOrElseUpdate[A](key: String, expiration: Duration)
                                   (orElse: => Future[A])
                                   (implicit evidence$1: ClassTag[A]): Future[A] = orElse

    override def get[T](key: String)
                       (implicit evidence$2: ClassTag[T]): Future[Option[T]] = ???

    override def removeAll(): Future[Done] = ???
  }

  "When set works in the repo returns a success result" in {
    when(adminDataRepository.getFeatureToggles).thenReturn(Future.successful(Seq.empty))
    when(adminDataRepository.setFeatureToggles(any())).thenReturn(Future.successful((): Unit))

    val OUT = injector.instanceOf[FeatureToggleService]
    val toggleName = arbitrary[FeatureToggleName].sample.value

    whenReady(OUT.set(toggleName = toggleName, enabled = true)) {
      _ =>
        val captor = ArgumentCaptor.forClass(classOf[Seq[FeatureToggle]])
        verify(adminDataRepository, times(1)).setFeatureToggles(captor.capture())
        captor.getValue must contain(Enabled(toggleName))
    }
  }

  "When set fails in the repo returns a success result" in {
    val toggleName = arbitrary[FeatureToggleName].sample.value

    val OUT = injector.instanceOf[FeatureToggleService]

    when(adminDataRepository.getFeatureToggles).thenReturn(Future.successful(Seq.empty))
    when(adminDataRepository.setFeatureToggles(any())).thenReturn(Future.successful((): Unit))

    whenReady(OUT.set(toggleName = toggleName, enabled = true))(_ mustBe ((): Unit))
  }

  "When getAll is called returns all of the toggles from the repo" in {
    val OUT = injector.instanceOf[FeatureToggleService]

    when(adminDataRepository.getFeatureToggles).thenReturn(Future.successful(Seq.empty))

    OUT.getAll.futureValue mustBe Seq(Disabled(DummyToggle))
  }

  "When a toggle doesn't exist in the repo, return default" in {
    when(adminDataRepository.getFeatureToggles).thenReturn(Future.successful(Seq.empty))
    val OUT = injector.instanceOf[FeatureToggleService]
    OUT.get(DummyToggle).futureValue mustBe Disabled(DummyToggle)
  }

  "When a toggle exists in the repo, override default" in {
    when(adminDataRepository.getFeatureToggles).thenReturn(Future.successful(Seq(Enabled(DummyToggle))))
    val OUT = injector.instanceOf[FeatureToggleService]
    OUT.get(DummyToggle).futureValue mustBe Enabled(DummyToggle)
  }
}
